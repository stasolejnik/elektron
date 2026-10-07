package pl.zse.bydgoszcz.elektron.presentation.subjects

import pl.zse.bydgoszcz.elektron.presentation.common.DelayedLoading
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.domain.model.SubjectStyles
import pl.zse.bydgoszcz.elektron.presentation.common.GroupedSection
import pl.zse.bydgoszcz.elektron.presentation.common.pressable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreen(onClose: () -> Unit, viewModel: SubjectsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Przedmioty") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                DelayedLoading { CircularProgressIndicator() }
            }
            state.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Brak przedmiotów - plan lekcji jeszcze się nie pobrał.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
            ) {
                item {
                    GroupedSection(
                        "Nazwy i kolory",
                        footer = "Zmiany widać w planie, na stronie głównej i w widżetach. " +
                            "Dotyczą wszystkich grup danego przedmiotu."
                    ) {
                        state.items.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 52.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SubjectRow(item) { editing = item.key }
                        }
                    }
                }
            }
        }
    }

    val current = state.items.firstOrNull { it.key == editing }
    if (current != null) {
        EditSubjectDialog(
            item = current,
            error = saveError, saving = saving,
            onSave = { name, color -> viewModel.save(current.key, name, color) { editing = null } },
            onReset = { viewModel.reset(current.key) { editing = null } },
            onDismiss = { if (!saving) { editing = null; viewModel.saveError.value = null } }
        )
    }
}

@Composable
private fun SubjectRow(item: SubjectsViewModel.Item, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorDot(item.style.color?.let { Color(it) }, size = 22)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(item.style.name ?: item.key, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.style.name != null) {
                Text("Na stronie szkoły: ${item.key}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color?, size: Int) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .then(if (color != null) Modifier.background(color) else Modifier.border(1.5.dp, outline, CircleShape))
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditSubjectDialog(
    item: SubjectsViewModel.Item,
    error: String?, saving: Boolean,
    onSave: (String?, Long?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(item.key) { mutableStateOf(item.style.name ?: item.key) }
    var color by rememberSaveable(item.key) { mutableStateOf(item.style.color) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.key) },
        text = {
            Column {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    enabled = !saving,
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nazwa w aplikacji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("Kolor", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ColorChoice(null, selected = color == null, label = "Bez koloru") { color = null }
                    SubjectStyles.PALETTE.forEachIndexed { i, c ->
                        val label = "Kolor " + (SubjectStyles.COLOR_NAMES.getOrNull(i) ?: "${i + 1}")
                        ColorChoice(c, selected = color == c, label = label) { color = c }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !saving, onClick = { onSave(name, color) }) { Text("Zapisz") } },
        dismissButton = {
            Row {
                if (!item.style.isDefault) TextButton(onClick = onReset) { Text("Przywróć") }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        }
    )
}

@Composable
private fun ColorChoice(color: Long?, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).pressable(onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        ColorDot(color?.let { Color(it) }, size = 36)
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null,
                tint = if (color != null) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp))
        }
    }
}
