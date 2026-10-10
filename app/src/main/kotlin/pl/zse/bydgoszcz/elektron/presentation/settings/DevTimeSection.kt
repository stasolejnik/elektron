package pl.zse.bydgoszcz.elektron.presentation.settings

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import pl.zse.bydgoszcz.elektron.domain.util.AppClock
import pl.zse.bydgoszcz.elektron.presentation.common.rememberNow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.unit.dp

/**
 * Tryb dewelopera: symulowany czas. Wybrany dzień i godzina dalej płyną; plan, zastępstwa,
 * strona główna, Odjazdy i widżety zachowują się jak w tej chwili - do "Wróć do czasu telefonu".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevTimeRows(offset: Long, onSimulate: (LocalDateTime) -> Unit, onReset: () -> Unit) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }   // epochDay wybranego dnia
    val now by rememberNow()
    val simulated = offset != 0L
    // Wiersze w sekcji "Tryb dewelopera" (bez osobnej sekcji).
    ValueRow(if (simulated) "Symulowany czas" else "Symulacja czasu",
        now.format(DateTimeFormatter.ofPattern("EEE d.MM, HH:mm", Locale("pl", "PL"))),
        supporting = if (simulated) "Plan, zastępstwa, Start, Odjazdy i widżety w tym czasie" else "Dotknij, aby wybrać dzień i godzinę",
        onClick = { pickingDate = true })
    if (simulated) {
        RowDivider()
        ActionRow("Wróć do czasu telefonu", destructive = true, onClick = onReset)
    }
    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = AppClock.today().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { pickedDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    pickingDate = false
                }, enabled = state.selectedDateMillis != null) { Text("Dalej") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Anuluj") } }
        ) { DatePicker(state = state) }
    }
    pickedDay?.let { day ->
        TimeDialog("Godzina", AppClock.time().withSecond(0).withNano(0),
            onPick = { time: LocalTime -> pickedDay = null; onSimulate(LocalDate.ofEpochDay(day).atTime(time)) },
            onDismiss = { pickedDay = null })
    }
}

/** Okna symulacji zastępstwa i ogłoszenia: wzór z zapisanych (także minionych) wpisów albo własny. */
@Composable
internal fun DevSimulationDialogs(
    dialog: SettingsViewModel.DevDialog?,
    onSubstitution: (pl.zse.bydgoszcz.elektron.work.DeveloperTools.SubstitutionDraft) -> Unit,
    onAnnouncement: (title: String, text: String, templateId: String?) -> Unit,
    onCustomSubstitution: () -> Unit,
    onCustomAnnouncement: () -> Unit,
    onDismiss: () -> Unit
) {
    val Drafts = pl.zse.bydgoszcz.elektron.work.DeveloperTools.SubstitutionDraft
    when (dialog) {
        null -> Unit
        is SettingsViewModel.DevDialog.Substitution -> {
            val options = listOf("custom" to "Własne…", "freed" to "Zwolnienie z lekcji") +
                dialog.past.mapIndexed { i, (_, label) -> "past$i" to label }
            ChoiceDialog("Symuluj zastępstwo", options, "", onSelect = { key ->
                when (key) {
                    "custom" -> onCustomSubstitution()
                    "freed" -> onSubstitution(Drafts.FREED)
                    else -> onSubstitution(dialog.past[key.removePrefix("past").toInt()].first)
                }
            }, onDismiss = onDismiss)
        }
        is SettingsViewModel.DevDialog.Announcement -> {
            val options = listOf("custom" to "Własne…") + dialog.past
            ChoiceDialog("Symuluj ogłoszenie", options, "", onSelect = { key ->
                if (key == "custom") onCustomAnnouncement() else onAnnouncement("", "", key)
            }, onDismiss = onDismiss)
        }
        SettingsViewModel.DevDialog.CustomSubstitution -> {
            var teacher by rememberSaveable { mutableStateOf("J. Testowy") }
            var room by rememberSaveable { mutableStateOf("108") }
            var notes by rememberSaveable { mutableStateOf("") }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Własne zastępstwo") },
                text = {
                    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        Text("Na najbliższej lekcji. Puste pole zastępcy - lekcja bez zastępcy (np. zwolnienie).")
                        androidx.compose.material3.OutlinedTextField(teacher, { teacher = it.take(80) }, label = { Text("Zastępca") }, singleLine = true)
                        androidx.compose.material3.OutlinedTextField(room, { room = it.take(80) }, label = { Text("Sala lub informacja") }, singleLine = true)
                        androidx.compose.material3.OutlinedTextField(notes, { notes = it.take(200) }, label = { Text("Uwagi") })
                    }
                },
                confirmButton = { TextButton(onClick = { onSubstitution(pl.zse.bydgoszcz.elektron.work.DeveloperTools.SubstitutionDraft(teacher, room, notes)) }) { Text("Symuluj") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
            )
        }
        SettingsViewModel.DevDialog.CustomAnnouncement -> {
            var title by rememberSaveable { mutableStateOf("Symulowane ogłoszenie") }
            var text by rememberSaveable { mutableStateOf("") }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Własne ogłoszenie") },
                text = {
                    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedTextField(title, { title = it.take(200) }, label = { Text("Tytuł") }, singleLine = true)
                        androidx.compose.material3.OutlinedTextField(text, { text = it.take(2000) }, label = { Text("Treść") }, minLines = 3)
                    }
                },
                confirmButton = { TextButton(onClick = { onAnnouncement(title, text, null) }) { Text("Symuluj") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
            )
        }
    }
}
