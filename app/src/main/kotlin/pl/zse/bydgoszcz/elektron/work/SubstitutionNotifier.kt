package pl.zse.bydgoszcz.elektron.work

import kotlinx.coroutines.flow.first
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionRelevance
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Powiadomienia o zastępstwach - jedna implementacja dla synchronizacji i pushy FCM
 * (dawniej skopiowana w SyncWorker i ElektronFirebaseMessagingService).
 *
 * Powiadamiamy tylko o zastępstwach klasy użytkownika, dla jego grup (LessonGroups),
 * i nie o takich, których lekcja już minęła. Przedmiot w treści - z grupy, której dotyczy
 * zastępstwo. Treść i ID powiadomień tworzy NotificationSink.
 */
@Singleton
class SubstitutionNotifier @Inject constructor(
    private val settings: SettingsRepository,
    private val timetableRepo: TimetableRepository,
    private val notificationsRepo: NotificationsRepository,
    private val sink: NotificationSink
) {

    /**
     * Po synchronizacji: powiadamia o [fresh] - zastępstwach jeszcze nie "widzianych".
     * Wołający trzyma NotificationsRepository.withNotifyLock i sam aktualizuje zbiór widzianych.
     */
    suspend fun notifyFresh(fresh: List<Substitution>) {
        if (fresh.isEmpty() || !settings.notificationsSubstitutions.first()) return
        relevant(fresh).forEach { (sub, subject) -> sink.postSubstitution(sub, subject) }
    }

    /**
     * Push FCM o jednym zastępstwie: powiadomienie, jeśli dotyczy użytkownika i nie było jeszcze
     * widziane (synchronizacja mogła je już obsłużyć) - pod tą samą blokadą co synchronizacja.
     */
    suspend fun notifyPushed(sub: Substitution) {
        if (!settings.notificationsSubstitutions.first()) return
        val (target, subject) = relevant(listOf(sub)).singleOrNull() ?: return
        notificationsRepo.withNotifyLock {
            val seen = notificationsRepo.getSeenSubstitutionIds()
            if (target.id in seen) return@withNotifyLock      // już obsłużone - bez duplikatu
            sink.postSubstitution(target, subject)
            notificationsRepo.setSeenSubstitutionIds(seen + target.id)
        }
    }

    /** Zastępstwa z [subs] dotyczące użytkownika (klasa, grupy, lekcja jeszcze trwa) + przedmiot. */
    private suspend fun relevant(subs: List<Substitution>): List<Pair<Substitution, String?>> {
        val classId = settings.selectedClassId.first() ?: return emptyList()
        val short = timetableRepo.observeClasses().first().firstOrNull { it.id == classId }?.shortName
            ?: return emptyList()
        val forClass = subs.filter { SubstitutionRelevance.matchesClass(it, short) }
        if (forClass.isEmpty()) return emptyList()
        // Grupy zajęciowe: pomijamy zastępstwa dla grupy, do której użytkownik nie należy.
        val groups = settings.groupSelections(classId).first()
        val lessons = runCatchingCancellable {
            timetableRepo.getLessonsOnce(classId, forClass.minOf { it.date }, forClass.maxOf { it.date })
        }.getOrDefault(emptyList())
        // Bez powiadomień o zastępstwach, których lekcja już minęła (np. poranne odczytane po południu).
        val ends = SubstitutionRelevance.lessonEnds(lessons)
        val now = LocalDateTime.now()
        val visibleLessons = LessonGroups.filter(lessons, groups)
        return forClass
            .filter { LessonGroups.substitutionRelevant(it, lessons, groups) }
            .filterNot { SubstitutionRelevance.isOver(it, now, ends) }
            .map { sub ->
                // Przedmiot grupy, której dotyczy zastępstwo.
                sub to visibleLessons
                    .firstOrNull { it.date == sub.date && it.number == sub.lessonNumber }
                    ?.let { LessonGroups.subjectFor(sub, it.groups) }
            }
    }
}
