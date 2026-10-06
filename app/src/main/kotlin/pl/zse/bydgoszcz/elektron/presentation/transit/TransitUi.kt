package pl.zse.bydgoszcz.elektron.presentation.transit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.presentation.common.*

@Composable
fun TransitVisibilitySection(viewModel: TransitViewModel) {
    val state by viewModel.settings.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.settingsError.collectAsStateWithLifecycle()
    GroupedSection("Pasek nawigacji", footer = "Ukryte zakładki nadal otworzysz przesunięciem ekranu. Ustawienia pozostają dostępne na pasku.") {
        bottomDestinationsForTransit(true).filter { it.route != "settings" }.forEachIndexed { index, destination ->
            if (index > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(destination.label, Modifier.weight(1f))
                val checked = if (destination.route == "transit") state.preferences.visible else destination.route !in state.preferences.hiddenTabs
                Switch(checked = checked, onCheckedChange = { viewModel.setTabVisible(destination.route, it) },
                    enabled = state.ready && !state.failed && !saving)
            }
        }
        if (state.failed) Text("Nie udało się odczytać ustawień paska.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StopPicker(viewModel: TransitViewModel, origin: Boolean = false, onDismiss: () -> Unit) {
    val query by viewModel.query.collectAsStateWithLifecycle()

    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val stale by viewModel.stale.collectAsStateWithLifecycle()
    val matches by viewModel.matchingStops.collectAsStateWithLifecycle()
    val origins by viewModel.originStops.collectAsStateWithLifecycle()
    val writeError by viewModel.settingsError.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val mapStops = if (origin) origins else matches
    val recent = remember(settings.preferences, mapStops, query, origin) {
        val history = if (origin) settings.preferences.recentOrigins else settings.preferences.recentDestinations
        val available = mapStops.associateBy { it.key }
        val words = pl.zse.bydgoszcz.elektron.presentation.announcements.SearchText.normalize(query).split(' ').filter { it.isNotBlank() }
        history.map { available[it.key] ?: it }.filter { stop ->
            val name = pl.zse.bydgoszcz.elektron.presentation.announcements.SearchText.normalize(stop.name)
            words.all { it in name } && (!origin || pl.zse.bydgoszcz.elektron.domain.model.SchoolTransit.distance(stop.latitude, stop.longitude) <= 1000)
        }.take(5)
    }
    val results = mapStops.filterNot { candidate -> recent.any { it.key == candidate.key } }.take(80)
    val choose: (pl.zse.bydgoszcz.elektron.domain.model.TransitDestination) -> Unit = {
        if (origin) viewModel.selectOrigin(it, onDismiss) else viewModel.select(it, onDismiss)
    }
    var mapMode by rememberSaveable { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val pickerOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(viewModel, pickerOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) viewModel.loadStops()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.cancelLoad()
        }
        pickerOwner.lifecycle.addObserver(observer)
        if (pickerOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) viewModel.loadStops()
        onDispose { pickerOwner.lifecycle.removeObserver(observer); viewModel.cancelLoad() }
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(modifier = Modifier.fillMaxSize().imePadding(), topBar = {
            TopAppBar(title = { Text(if (origin) "Przystanek początkowy" else "Przystanek docelowy") }, navigationIcon = {
                IconButton(onClick = onDismiss, enabled = !saving) { Icon(Icons.Filled.Close, "Zamknij wybór przystanku") }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                TabRow(selectedTabIndex = if (mapMode) 1 else 0) {
                    Tab(selected = !mapMode, onClick = { mapMode = false }, text = { Text("Lista") })
                    Tab(selected = mapMode, onClick = { focus.clearFocus(); keyboard?.hide(); mapMode = true }, text = { Text("Mapa") })
                }
                if (origin) TextButton(onClick = { viewModel.selectOrigin(null, onDismiss) }, enabled = !saving) {
                    Text("Automatycznie wybierz najbliższy")
                }
                if (origin) Text("Przystanki do 1 km od szkoły", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = query, onValueChange = { viewModel.query.value = it.take(200) },
                    label = { Text("Szukaj przystanku") }, placeholder = { Text("Wpisz nazwę, np. Rondo Jagiellonów") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !saving,
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { viewModel.query.value = "" }, enabled = !saving) {
                        Icon(Icons.Filled.Close, "Wyczyść wyszukiwanie")
                    } })
                if (loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                if (stale) Text("Korzystasz z zapisanego katalogu. Nie udało się go odświeżyć.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                if (error != null || stale) {
                    TextButton(onClick = { viewModel.loadStops(force = true) }, enabled = !loading && !saving) { Text("Spróbuj ponownie") }
                }
                writeError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                if (!loading && mapStops.isEmpty() && recent.isEmpty() && error == null) Text("Nie znaleziono przystanku. Spróbuj krótszej nazwy.", Modifier.padding(vertical = 16.dp))
                if (mapMode) {
                    Text("Dotknij znacznika, aby wybrać przystanek. Mapa działa bez GPS.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    Box(Modifier.weight(1f)) {
                        TransitMap(mapStops, if (origin) settings.preferences.preferredOrigin else settings.preferences.destination, saving, choose)
                    }
                } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (recent.isNotEmpty()) {
                        item(key = "recent-header") { Text("Ostatnie", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface) }
                        items(recent, key = { "recent-${it.key}" }) { stop ->
                            ElektronCard(onClick = if (saving) null else ({ choose(stop) }), onClickLabel = "Wybierz przystanek") {
                                Text(stop.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(16.dp))
                            }
                        }
                        item(key = "all-header") { Text(if (origin) "Od najbliższego szkoły" else "Wszystkie przystanki", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface) }
                    }
                    items(results, key = { it.key }) { stop ->
                        ElektronCard(onClick = if (saving) null else ({ choose(stop) }), onClickLabel = "Wybierz przystanek") {
                            Text(stop.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                        }
                    }
                    if (results.size == 80) item { Text("Wpisz dokładniejszą nazwę, aby zawęzić listę.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
