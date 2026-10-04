package pl.zse.bydgoszcz.elektron.presentation.timetable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.domain.model.*
import pl.zse.bydgoszcz.elektron.presentation.common.LocalPersonalization
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessonNoteEditor(lesson: Lesson, state: LessonNotesViewModel.State,
    viewModel: LessonNotesViewModel, onDismiss: () -> Unit) {
    val note = state.notes.firstOrNull { it.key == LessonNote.key(lesson) }
    var text by rememberSaveable(LessonNote.key(lesson)) { mutableStateOf(note?.text.orEmpty()) }
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = text != note?.text.orEmpty()
    val dismiss = { if (!saving) { if (dirty) confirmDiscard = true else onDismiss() } }
    LaunchedEffect(lesson.id) { viewModel.clearError() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { target ->
        if (target == SheetValue.Hidden && (saving || dirty)) {
            if (!saving) confirmDiscard = true
            false
        } else true
    })
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notatka do lekcji", style = MaterialTheme.typography.headlineSmall)
            Text(lesson.groups.mapNotNull { LocalPersonalization.current.subjectName(it.subject) }.distinct().joinToString(" / ").ifBlank { "Lekcja" },
                style = MaterialTheme.typography.titleMedium)
            Text("${lesson.date.format(DateTimeFormatter.ofPattern("d.MM.yyyy"))} · ${lesson.number}. lekcja · ${lesson.timeFrom}",
                style = MaterialTheme.typography.bodyMedium)
            if (note != null && note.subject != LessonNote.subject(lesson)) Text(
                "Plan tej lekcji się zmienił. Notatka była zapisana dla: ${note.subject}.", color = MaterialTheme.colorScheme.tertiary)
            OutlinedTextField(value = text, onValueChange = { if (it.length <= LessonNote.MAX_LENGTH) text = it },
                label = { Text("Twoja notatka") }, placeholder = { Text("Np. powtórzyć rozdział 3") },
                minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth(), enabled = !saving,
                supportingText = { Text("${text.length}/${LessonNote.MAX_LENGTH}") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Przypomnienia możesz włączyć w Ustawieniach → Notatki do lekcji.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (note != null) TextButton(onClick = { confirmDelete = true }, enabled = !saving) { Text("Usuń") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = dismiss, enabled = !saving) { Text("Anuluj") }
                Button(onClick = { viewModel.save(lesson, text, onDismiss) }, enabled = !saving && text.isNotBlank() && dirty) {
                    Text(if (saving) "Zapisuję…" else "Zapisz")
                }
            }
        }
    }
    if (confirmDelete && note != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Usunąć notatkę?") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(note, onDismiss) }) { Text("Usuń") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } })
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Odrzucić niezapisane zmiany?") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text("Odrzuć") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Wróć do notatki") } })
}
