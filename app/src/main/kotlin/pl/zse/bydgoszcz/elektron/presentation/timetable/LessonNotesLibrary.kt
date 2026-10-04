package pl.zse.bydgoszcz.elektron.presentation.timetable

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.MainActivity
import pl.zse.bydgoszcz.elektron.domain.model.*
import java.time.format.DateTimeFormatter

/** Dostęp również do notatek ze starych lub usuniętych z planu lekcji. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessonNotesLibrary(notes: List<LessonNote>, viewModel: LessonNotesViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<LessonNote?>(null) }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.clearError() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Moje notatki", style = MaterialTheme.typography.headlineSmall) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (notes.isEmpty()) item { Text("Brak notatek w wybranej klasie. Przytrzymaj przyszłą lekcję w planie, aby dodać pierwszą.") }
            items(notes.sortedWith(compareByDescending<LessonNote> { it.date }.thenBy { it.number }), key = { it.key }) { note ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${note.date.format(DateTimeFormatter.ofPattern("d.MM.yyyy"))} · ${note.number}. lekcja", style = MaterialTheme.typography.titleMedium)
                        Text(note.subject, style = MaterialTheme.typography.bodySmall)
                        SelectionContainer { Text(note.text) }
                        Row {
                            TextButton(onClick = {
                                onDismiss()
                                context.startActivity(Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra(LessonLinks.EXTRA_LESSON, LessonLinks.deepLink(LessonTarget(note.date, note.number)))
                                })
                            }) { Text("Otwórz w planie") }
                            TextButton(onClick = { deleting = note }) { Text("Usuń") }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { note -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Usunąć notatkę?") },
        confirmButton = { TextButton(onClick = { viewModel.delete(note) { deleting = null } }, enabled = !saving) { Text("Usuń") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Anuluj") } }) }
}
