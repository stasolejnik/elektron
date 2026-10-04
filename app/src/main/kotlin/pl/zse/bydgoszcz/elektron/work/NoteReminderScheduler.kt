package pl.zse.bydgoszcz.elektron.work

import android.content.Context
import android.util.Log
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.zse.bydgoszcz.elektron.data.repository.LessonNotesRepository
import pl.zse.bydgoszcz.elektron.domain.model.*
import pl.zse.bydgoszcz.elektron.domain.repository.*
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import java.time.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Jeden lokalny termin; WorkManager zachowuje go po restarcie, bez cyklicznego odpytywania. */
@Singleton
class NoteReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notes: LessonNotesRepository,
    private val settings: SettingsRepository,
    private val timetable: TimetableRepository,
    private val notifications: LocalNotificationSink
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    @Volatile private var scheduled: String? = null
    private var cleared = false
    fun requestReschedule(force: Boolean = false) { scope.launch { reschedule(force = force) } }
    suspend fun reschedule(now: LocalDateTime = LocalDateTime.now(), force: Boolean = false) = mutex.withLock { rescheduleLocked(now, force) }
    private suspend fun rescheduleLocked(now: LocalDateTime, force: Boolean = false) {
        runCatchingCancellable {
            val reminder = next(now)
            val wm = WorkManager.getInstance(context)
            if (reminder == null) {
                if (!cleared) awaitOperation(wm.cancelUniqueWork(WORK_NAME))
                scheduled = null; cleared = true
            } else {
                val planned = reminder.plannedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val key = "${reminder.note.key}/${reminder.note.revision}/$planned"
                if (!force && key == scheduled) return@runCatchingCancellable
                val delay = Duration.between(now.atZone(ZoneId.systemDefault()).toInstant(),
                    reminder.at.atZone(ZoneId.systemDefault()).toInstant()).toMillis().coerceAtLeast(0)
                awaitOperation(wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<NoteReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setInputData(workDataOf("key" to reminder.note.key, "revision" to reminder.note.revision)).build()))
                scheduled = key; cleared = false
            }
        }.onFailure { Log.w("NoteReminders", "Nie udało się ustawić przypomnienia notatki", it) }
    }
    private suspend fun awaitOperation(operation: Operation) = suspendCancellableCoroutine<Unit> { continuation ->
        val future = operation.result
        future.addListener({
            if (continuation.isActive) continuation.resumeWith(runCatching { future.get(); Unit })
        }, java.util.concurrent.Executor { it.run() })
    }
    private suspend fun eligibleLessons(now: LocalDateTime): Pair<List<LessonNote>, List<Lesson>> {
        val classId = settings.selectedClassId.first() ?: return emptyList<LessonNote>() to emptyList()
        val all = notes.notes.first().filter { it.classId == classId && it.date >= now.toLocalDate() }
        if (all.isEmpty()) return all to emptyList()
        val lessons = LessonGroups.filter(timetable.getLessonsOnce(classId, now.toLocalDate(), all.maxOf { it.date }),
            settings.groupSelections(classId).first())
        return all to lessons
    }
    private suspend fun next(now: LocalDateTime): NoteReminders.Reminder? {
        if (!notes.remindersEnabled.first() || !notifications.canPostNotes()) return null
        val (all, lessons) = eligibleLessons(now)
        return NoteReminders.next(all, lessons, now, notes.reminderTiming.first())
    }
    suspend fun deliver(key: String, revision: Long, now: LocalDateTime = LocalDateTime.now()) = mutex.withLock {
        // Nie korzystamy z tekstu zapisanego w zleceniu: edycja, usunięcie, zmiana klasy
        // i odwołanie lekcji muszą obowiązywać także tuż przed wysłaniem powiadomienia.
        if (notes.remindersEnabled.first() && notifications.canPostNotes()) {
            val (all, lessons) = eligibleLessons(now)
            if (all.any { it.key == key && it.revision == revision && !it.reminded }) {
                // Kilka notatek na jutro ma ten sam termin 18:00 — dostarczamy je w jednym przebiegu.
                val timing = notes.reminderTiming.first()
                val byKey = lessons.associateBy(LessonNote::key)
                for (note in all.filterNot { it.reminded }) {
                    val lesson = byKey[note.key] ?: continue
                    if (!LessonNote.canEdit(lesson, now) || !NoteReminders.matchesGroups(note, lesson) || NoteReminders.plannedAt(note, lesson, timing) > now ||
                        lesson.substitution?.let(SubstitutionDisplay::freesLesson) == true) continue
                    if (notes.claimReminder(note.key, note.revision)) {
                        val title = LessonReminders.describe(lesson, settings.subjectStyles.first()).first
                        notifications.postNote(note, title, note.subject != LessonNote.subject(lesson))
                    }
                }
            }
        }
        // Nie zatrzymujemy się po pierwszej notatce: planujemy następny termin.
        scheduled = null
        rescheduleLocked(now)
    }
    companion object { const val WORK_NAME = "lesson_note_reminder" }
}
