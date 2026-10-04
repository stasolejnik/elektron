package pl.zse.bydgoszcz.elektron.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import pl.zse.bydgoszcz.elektron.domain.model.NoteReminderSettings
import pl.zse.bydgoszcz.elektron.domain.model.LessonNote
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lessonNotesStore by preferencesDataStore(name = "lesson_notes")

/** Dane użytkownika poza bazą podręczną; zapis zgłasza błędy zamiast udawać sukces. */
@Singleton
class LessonNotesRepository internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.lessonNotesStore)
    private val entries = stringPreferencesKey("notes")
    private val reminders = booleanPreferencesKey("reminders_enabled")
    private val previousDay = booleanPreferencesKey("reminder_previous_day")
    private val reminderTime = intPreferencesKey("reminder_time_minutes")
    private val minutesBefore = intPreferencesKey("reminder_minutes_before")
    val reminderTiming: Flow<NoteReminderSettings> = store.data.map {
        NoteReminderSettings(previousDay = it[previousDay] ?: true,
            time = java.time.LocalTime.ofSecondOfDay(((it[reminderTime] ?: 1080).coerceIn(0, 1439) * 60).toLong()),
            minutesBefore = (it[minutesBefore] ?: 60).takeIf { n -> n in NoteReminderSettings.MINUTE_OPTIONS } ?: 60)
    }.distinctUntilChanged()
    suspend fun setReminderTiming(value: NoteReminderSettings) = withContext(Dispatchers.IO) {
        store.edit { it[previousDay] = value.previousDay; it[reminderTime] = value.time.hour * 60 + value.time.minute; it[minutesBefore] = value.minutesBefore }; Unit
    }
    val notes: Flow<List<LessonNote>> = store.data.map { it[entries] }.distinctUntilChanged().map(::decode).flowOn(Dispatchers.Default)
    val remindersEnabled: Flow<Boolean> = store.data.map { it[reminders] ?: false }.distinctUntilChanged()
    suspend fun setReminders(enabled: Boolean) = withContext(Dispatchers.IO) { store.edit { it[reminders] = enabled }; Unit }
    suspend fun save(note: LessonNote) = withContext(Dispatchers.IO) {
        require(note.text.length <= LessonNote.MAX_LENGTH)
        store.edit { prefs ->
            val all = decode(prefs[entries]).toMutableList()
            val old = all.firstOrNull { it.key == note.key }
            all.removeAll { it.key == note.key }
            if (note.text.isNotBlank()) all += note.copy(text = note.text.trim(),
                revision = maxOf(System.currentTimeMillis(), (old?.revision ?: 0L) + 1), reminded = false)
            prefs[entries] = encode(all)
        }
    }
    suspend fun delete(key: String) = withContext(Dispatchers.IO) { store.edit { it[entries] = encode(decode(it[entries]).filterNot { n -> n.key == key }) }; Unit }
    /** Atomowo blokuje podwójne powiadomienie i nie zmienia nowszej edycji notatki. */
    suspend fun claimReminder(key: String, revision: Long): Boolean = withContext(Dispatchers.IO) {
        var claimed = false
        store.edit { prefs ->
            prefs[entries] = encode(decode(prefs[entries]).map { note ->
                if (note.key == key && note.revision == revision && !note.reminded) {
                    claimed = true; note.copy(reminded = true)
                } else note
            })
        }
        claimed
    }
    private fun encode(notes: List<LessonNote>): String = JSONArray().apply {
        notes.forEach { n -> put(JSONObject().put("class", n.classId).put("date", n.date.toString())
            .put("number", n.number).put("subject", n.subject).put("text", n.text)
            .put("revision", n.revision).put("reminded", n.reminded)) }
    }.toString()
    private fun decode(value: String?): List<LessonNote> {
        if (value == null) return emptyList()
        val array = JSONArray(value)
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            LessonNote(it.getString("class"), LocalDate.parse(it.getString("date")), it.getInt("number"),
                it.getString("subject"), it.getString("text"), it.getLong("revision"), it.optBoolean("reminded"))
        } }
    }
}
