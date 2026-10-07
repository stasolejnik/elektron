package pl.zse.bydgoszcz.elektron.presentation.settings

import android.app.NotificationManager
import android.os.Build
import pl.zse.bydgoszcz.elektron.domain.model.NoteReminderSettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import pl.zse.bydgoszcz.elektron.presentation.common.*
import pl.zse.bydgoszcz.elektron.work.LocalNotificationSink
import androidx.core.app.NotificationManagerCompat

@Composable
internal fun NoteRemindersSection(enabled: Boolean, ready: Boolean, timing: NoteReminderSettings, onTimingChange: (NoteReminderSettings) -> Unit, onResume: () -> Unit, onChange: (Boolean) -> Unit, onOpenNotes: () -> Unit) {
    var choosingMode by remember { mutableStateOf(false) }
    var choosingTime by remember { mutableStateOf(false) }
    var choosingMinutes by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun canNotify() = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(LocalNotificationSink.CHANNEL_NOTES)?.importance != NotificationManager.IMPORTANCE_NONE)
    var allowed by remember { mutableStateOf(canNotify()) }
    OnResume { allowed = canNotify(); onResume() }
    GroupedSection("Notatki do lekcji", footer = "Przytrzymaj przyszłą lekcję w planie, aby zapisać notatkę. Wybierz dzień wcześniej i godzinę albo liczbę minut przed lekcją. Gdy dodasz notatkę zbyt późno, przypomnienie przyjdzie przed lekcją lub możliwie szybko. System może opóźnić powiadomienie.") {
        if (!ready) Text("Nie udało się jeszcze odczytać notatek.", style = MaterialTheme.typography.bodySmall)
        ActionRow("Moje notatki", trailingChevron = true) { if (ready) onOpenNotes() }
        RowDivider()
        SwitchRow("Przypomnienia o notatkach", enabled, onChange = onChange)
        if (enabled) {
            RowDivider()
            ValueRow("Kiedy", if (timing.previousDay) "Dzień wcześniej" else "Przed lekcją") { choosingMode = true }
            RowDivider()
            if (timing.previousDay) ValueRow("Godzina", timing.time.toString()) { choosingTime = true }
            else ValueRow("Ile wcześniej", "${timing.minutesBefore} min") { choosingMinutes = true }
        }
        if (enabled && !allowed) {
            RowDivider()
            ActionRow("Włącz powiadomienia", trailingChevron = true) { openNotificationSettings(context) }
        }
    }
    if (choosingMode) ChoiceDialog("Kiedy przypominać", listOf(true to "Dzień wcześniej", false to "Przed lekcją"), timing.previousDay,
        onSelect = { onTimingChange(timing.copy(previousDay = it)); choosingMode = false }, onDismiss = { choosingMode = false })
    if (choosingTime) TimeDialog("Godzina przypomnienia", timing.time,
        onPick = { onTimingChange(timing.copy(time = it)); choosingTime = false }, onDismiss = { choosingTime = false })
    if (choosingMinutes) ChoiceDialog("Ile wcześniej", NoteReminderSettings.MINUTE_OPTIONS.map { it to "$it min przed lekcją" }, timing.minutesBefore,
        onSelect = { onTimingChange(timing.copy(minutesBefore = it)); choosingMinutes = false }, onDismiss = { choosingMinutes = false })
}
