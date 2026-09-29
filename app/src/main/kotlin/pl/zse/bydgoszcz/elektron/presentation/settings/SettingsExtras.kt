package pl.zse.bydgoszcz.elektron.presentation.settings

import pl.zse.bydgoszcz.elektron.domain.model.SchoolClass
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import pl.zse.bydgoszcz.elektron.domain.model.LessonReminders
import pl.zse.bydgoszcz.elektron.domain.model.QuietHours
import pl.zse.bydgoszcz.elektron.domain.model.ReminderMode
import pl.zse.bydgoszcz.elektron.domain.model.ReminderSettings
import pl.zse.bydgoszcz.elektron.domain.model.StartScreen
import pl.zse.bydgoszcz.elektron.domain.model.WidgetLook
import pl.zse.bydgoszcz.elektron.presentation.common.GroupedSection
import pl.zse.bydgoszcz.elektron.presentation.common.pressable
import pl.zse.bydgoszcz.elektron.work.LessonReminderScheduler
import java.time.LocalTime

// Sekcje Ustawień dodane w 0.6: ekran startowy, przypomnienia, ciche godziny, widżety.

private val START_OPTIONS = listOf(
    StartScreen.SMART to "Automatycznie",
    StartScreen.DASHBOARD to "Strona główna",
    StartScreen.TIMETABLE to "Plan lekcji",
    StartScreen.SUBSTITUTIONS to "Zastępstwa"
)

private val REMINDER_OPTIONS = listOf(
    ReminderMode.OFF to "Wyłączone",
    ReminderMode.FIRST to "Przed pierwszą lekcją",
    ReminderMode.EVERY to "Przed każdą lekcją"
)

@Composable
internal fun StartScreenSection(current: StartScreen, onChange: (StartScreen) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    GroupedSection(
        "Uruchamianie",
        footer = if (current == StartScreen.SMART)
            "W godzinach lekcji aplikacja otwiera się na planie, poza nimi - na stronie głównej."
        else null
    ) {
        ValueRow("Ekran startowy", START_OPTIONS.first { it.first == current }.second) { choosing = true }
    }
    if (choosing) {
        ChoiceDialog("Ekran startowy", START_OPTIONS, current,
            onSelect = { onChange(it); choosing = false }, onDismiss = { choosing = false })
    }
}

@Composable
internal fun RemindersSection(current: ReminderSettings, onChange: (ReminderSettings) -> Unit) {
    val ctx = LocalContext.current
    var choosingMode by remember { mutableStateOf(false) }
    var choosingMinutes by remember { mutableStateOf(false) }
    // Zgoda na dokładne alarmy i na powiadomienia - sprawdzane przy każdym powrocie z ustawień systemu.
    var exact by remember { mutableStateOf(LessonReminderScheduler.canUseExactAlarms(ctx)) }
    var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    OnResume {
        notificationsOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        val now = LessonReminderScheduler.canUseExactAlarms(ctx)
        if (now != exact) {
            exact = now
            onChange(current)       // przeliczenie alarmu w nowym trybie
        }
    }

    val enabled = current.mode != ReminderMode.OFF
    GroupedSection(
        "Przypomnienia o lekcjach",
        footer = when {
            !enabled -> "Powiadomienie kilka minut przed lekcją - z salą, a przy zastępstwie z zastępcą."
            !notificationsOn -> "Powiadomienia eLektronu są wyłączone w systemie - przypomnienia się nie pokażą."
            !exact -> "Bez zgody na alarmy przypomnienie może przyjść kilka minut później."
            else -> "Zmiany w planie i zastępstwa są uwzględniane automatycznie."
        }
    ) {
        ValueRow("Przypomnienia", REMINDER_OPTIONS.first { it.first == current.mode }.second) { choosingMode = true }
        if (enabled) {
            RowDivider()
            ValueRow("Ile wcześniej", "${current.minutesBefore} min") { choosingMinutes = true }
            if (!notificationsOn) {
                RowDivider()
                ActionRow("Włącz powiadomienia", trailingChevron = true) { openNotificationSettings(ctx) }
            }
            if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RowDivider()
                ActionRow("Zezwól na alarmy o czasie", trailingChevron = true) { openExactAlarmSettings(ctx) }
            }
        }
    }
    if (choosingMode) {
        ChoiceDialog("Przypomnienia", REMINDER_OPTIONS, current.mode,
            onSelect = { onChange(current.copy(mode = it)); choosingMode = false },
            onDismiss = { choosingMode = false })
    }
    if (choosingMinutes) {
        ChoiceDialog("Ile wcześniej", LessonReminders.MINUTE_OPTIONS.map { it to "$it min przed lekcją" }, current.minutesBefore,
            onSelect = { onChange(current.copy(minutesBefore = it)); choosingMinutes = false },
            onDismiss = { choosingMinutes = false })
    }
}

@Composable
internal fun QuietHoursSection(current: QuietHours, onChange: (QuietHours) -> Unit) {
    var picking by remember { mutableStateOf<Boolean?>(null) }   // true = od, false = do
    GroupedSection(
        "Ciche godziny",
        footer = "W tych godzinach powiadomienia przychodzą bez dźwięku i wibracji - nic nie ginie."
    ) {
        SwitchRow("Ciche godziny", current.enabled) { onChange(current.copy(enabled = it)) }
        if (current.enabled) {
            RowDivider()
            ValueRow("Od", current.from.toString()) { picking = true }
            RowDivider()
            ValueRow("Do", current.to.toString()) { picking = false }
        }
    }
    picking?.let { from ->
        TimeDialog(
            title = if (from) "Ciche godziny od" else "Ciche godziny do",
            initial = if (from) current.from else current.to,
            onPick = { t -> onChange(if (from) current.copy(from = t) else current.copy(to = t)); picking = null },
            onDismiss = { picking = null }
        )
    }
}

@Composable
internal fun WidgetsSection(current: WidgetLook, onChange: (WidgetLook) -> Unit) {
    GroupedSection("Widżety", footer = "Przezroczystość tła i zawartość wszystkich widżetów aplikacji.") {
        Text("Krycie tła", style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val options = WidgetLook.OPACITY_OPTIONS
            options.forEachIndexed { i, value ->
                SegmentedButton(
                    selected = current.opacity == value,
                    onClick = { onChange(current.copy(opacity = value)) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    icon = {}
                ) { Text("$value%") }
            }
        }
        RowDivider()
        SwitchRow("Pokazuj nauczyciela", current.showTeacher) { onChange(current.copy(showTeacher = it)) }
        RowDivider()
        SwitchRow("Pokazuj salę", current.showRoom) { onChange(current.copy(showRoom = it)) }
    }
}

/** Klasa: wiersz z wybraną klasą, wybór w oknie (bez przewijanej listy wewnątrz Ustawień). */
@Composable
internal fun ClassSection(classes: List<SchoolClass>, selectedId: String?, onSelect: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    GroupedSection("Klasa", footer = "Plan, zastępstwa i powiadomienia dotyczą wybranej klasy.") {
        if (classes.isEmpty()) {
            Text("Brak listy klas. Pociągnij w dół na stronie głównej, aby odświeżyć.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val current = classes.firstOrNull { it.id == selectedId }
            ValueRow("Twoja klasa", current?.fullName ?: "wybierz") { choosing = true }
        }
    }
    if (choosing) {
        ChoiceDialog("Twoja klasa", classes.map { it.id to it.fullName }, selectedId ?: "",
            onSelect = { onSelect(it); choosing = false }, onDismiss = { choosing = false })
    }
}

/** Wiersz "etykieta ... wartość" otwierający wybór. */
@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // Długie listy (klasy) przewijają się wewnątrz okna, od razu przy zaznaczonej pozycji.
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (options.indexOfFirst { it.first == selected } - 2).coerceAtLeast(0)
            )
            LazyColumn(Modifier.selectableGroup(), state = listState) {
                items(options) { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        // TimeInput zamiast tarczy zegara - tarcza nie mieści się w oknie na wąskich ekranach.
        text = { TimeInput(state = state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@Composable
private fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(block)   // bez tego obserwator wołałby blok z pierwszym stanem
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) latest() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** Systemowe ustawienia powiadomień eLektronu. */
private fun openNotificationSettings(ctx: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }
        .onFailure {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
}

/** Android 12+: systemowa zgoda "Alarmy i przypomnienia" dla eLektronu. */
private fun openExactAlarmSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }
        .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}
