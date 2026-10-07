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
    val text by viewModel.editorText.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = text != note?.text.orEmpty()
    val dismiss = { if (!saving) { if (dirty) confirmDiscard = true else onDismiss() } }
    val available by viewModel.editorAvailable.collectAsStateWithLifecycle()
    val sheetState = rememberNoteSheetState(dirty, saving) { confirmDiscard = true }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notatka do lekcji", style = MaterialTheme.typography.headlineSmall)
            Text(lesson.groups.mapNotNull { LocalPersonalization.current.subjectName(it.subject) }.distinct().joinToString(" / ").ifBlank { "Lekcja" },
                style = MaterialTheme.typography.titleMedium)
            Text("${lesson.date.format(DateTimeFormatter.ofPattern("d.MM.yyyy"))} · ${lesson.number}. lekcja · ${lesson.timeFrom}",
                style = MaterialTheme.typography.bodyMedium)
            if (note != null && note.subject != LessonNote.subject(lesson)) Text(
                "Plan tej lekcji się zmienił. Notatka była zapisana dla: ${note.subject}.", color = MaterialTheme.colorScheme.tertiary)
            OutlinedTextField(value = text, onValueChange = { if (it.length <= LessonNote.MAX_LENGTH) viewModel.editText(it) },
                label = { Text("Twoja notatka") }, placeholder = { Text("Np. powtórzyć rozdział 3") },
                minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth(), enabled = !saving,
                supportingText = { Text("${text.length}/${LessonNote.MAX_LENGTH}") })
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }) { Text("Kopiuj szkic") }
            }
            if (!LessonNote.canEdit(lesson, java.time.LocalDateTime.now())) Text("Lekcja już się rozpoczęła. Zapis zmieni tekst notatki, ale nie wyśle przypomnienia o tej lekcji.", style = MaterialTheme.typography.bodySmall)
            Text("Przypomnienia możesz włączyć w Ustawieniach → Notatki do lekcji.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (note != null) TextButton(onClick = { confirmDelete = true }, enabled = !saving) { Text("Usuń") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = dismiss, enabled = !saving) { Text("Anuluj") }
                Button(onClick = { viewModel.save(lesson, text, onDismiss) }, enabled = !saving && available && text.isNotBlank() && dirty) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberNoteSheetState(dirty: Boolean, saving: Boolean, onDiscard: () -> Unit): SheetState {
    val confirm = rememberNoteSheetConfirmation(dirty, saving, onDiscard)
    return rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = confirm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberNoteSheetConfirmation(dirty: Boolean, saving: Boolean, onDiscard: () -> Unit): (SheetValue) -> Boolean {
    val latestDirty by rememberUpdatedState(dirty)
    val latestSaving by rememberUpdatedState(saving)
    val latestDiscard by rememberUpdatedState(onDiscard)
    // Material 3 keys rememberSaveable by this callback. A new callback on every edit
    // recreates the sheet in Hidden state and starts its opening animation again.
    val confirm: (SheetValue) -> Boolean = remember {
        { target ->
            if (target == SheetValue.Hidden && (latestSaving || latestDirty)) {
                if (!latestSaving) latestDiscard()
                false
            } else true
        }
    }
    return confirm
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RestoredNoteDraft(viewModel: LessonNotesViewModel) {
    val text by viewModel.editorText.collectAsStateWithLifecycle()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var discard by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { discard = true }, sheetState = rememberNoteSheetState(true, false) { discard = true }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Zachowany szkic notatki", style = MaterialTheme.typography.headlineSmall)
            Text("Nie udało się odnaleźć lekcji. Skopiuj treść, zanim odrzucisz szkic.")
            androidx.compose.foundation.text.selection.SelectionContainer { Text(text) }
            Row {
                TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }) { Text("Kopiuj szkic") }
                TextButton(onClick = { discard = true }) { Text("Odrzuć szkic") }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Odrzucić zachowany szkic?") },
        confirmButton = { TextButton(onClick = { viewModel.closeEditor() }) { Text("Odrzuć") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Wróć") } })
}
