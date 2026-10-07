package pl.zse.bydgoszcz.elektron.presentation.transit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.domain.model.*
import pl.zse.bydgoszcz.elektron.presentation.common.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val transitTime = DateTimeFormatter.ofPattern("HH:mm")
private val transitZone = ZoneId.of("Europe/Warsaw")
internal fun formatTransitTime(timeMs: Long, referenceMs: Long = System.currentTimeMillis()): String {
    val time = Instant.ofEpochMilli(timeMs).atZone(transitZone)
    val today = Instant.ofEpochMilli(referenceMs).atZone(transitZone).toLocalDate()
    val prefix = when (time.toLocalDate()) {
        today -> ""
        today.plusDays(1) -> "jutro "
        else -> time.format(DateTimeFormatter.ofPattern("d.MM")) + " "
    }
    return prefix + time.format(transitTime)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitScreen(viewModel: TransitViewModel, onClose: (() -> Unit)? = null) {
    val state by viewModel.settings.collectAsStateWithLifecycle()
    val result by viewModel.journeys.collectAsStateWithLifecycle()
    val loading by viewModel.journeyLoading.collectAsStateWithLifecycle()
    val moreLoading by viewModel.moreLoading.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableIntStateOf(0) }
    var visibleCount by rememberSaveable(state.preferences.destination?.key, state.preferences.preferredOrigin?.key, state.preferences.allowTransfers) { mutableIntStateOf(3) }
    val error by viewModel.journeyError.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val settingsError by viewModel.settingsError.collectAsStateWithLifecycle()
    val visible = LocalScreenVisible.current
    val requestedCount by rememberUpdatedState(visibleCount)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(visible, state.preferences.destination?.key, state.preferences.preferredOrigin?.key, state.preferences.allowTransfers, owner) {
        val active = visible
        val consumer = Any()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && active) viewModel.startJourneySession(consumer, minimumCount = requestedCount)
            if (event == Lifecycle.Event.ON_STOP) viewModel.stopJourneySession(consumer)
        }
        owner.lifecycle.addObserver(observer)
        if (active && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.startJourneySession(consumer, minimumCount = requestedCount)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.stopJourneySession(consumer) }
    }
    val tick by rememberNow(60_000L)
    LaunchedEffect(visible, tick, state.preferences.destination?.key, state.preferences.preferredOrigin?.key, state.preferences.allowTransfers) {
        if (visible && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.refreshJourneys(requestedCount)
    }
    val nowMs = remember(tick) { System.currentTimeMillis() }
    val matching = result?.takeIf { it.destinationKey == state.preferences.destination?.key && it.originKey == state.preferences.preferredOrigin?.key && it.allowTransfers == state.preferences.allowTransfers }
    val journeys = remember(matching, nowMs, state.preferences.preferredOrigin, state.preferences.allowTransfers) {
        TransitJourneys.upcoming(matching?.journeys.orEmpty(), nowMs, state.preferences.preferredOrigin, state.preferences.allowTransfers)
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        if (onClose == null) LargeTitleBar("Odjazdy", scroll)
        else TopAppBar(title = { Text("Odjazdy") }, navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Zamknij Odjazdy") }
        })
    },
        containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ElektronCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ze szkoły · Karłowicza 20", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.preferences.destination?.name ?: "Wybierz cel", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.query.value = ""; picker = 1 }, enabled = state.ready && !state.failed && !saving, modifier = Modifier.weight(1f)) {
                        Text(if (state.preferences.destination == null) "Wybierz przystanek docelowy" else "Zmień przystanek docelowy")
                    }
                    if (state.preferences.destination != null) IconButton(onClick = viewModel::clear, enabled = !saving) {
                        Icon(Icons.Filled.Close, "Usuń przystanek docelowy")
                    }
                }
                HorizontalDivider()
                Text("Odjazd z przystanku", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.preferences.preferredOrigin?.name ?: "Najbliższy odpowiedni", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { viewModel.query.value = ""; picker = 2 }, enabled = state.ready && !state.failed && !saving) { Text("Wybierz preferowany przystanek") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pokaż połączenia z przesiadką", Modifier.weight(1f))
                    Switch(checked = state.preferences.allowTransfers, onCheckedChange = viewModel::setAllowTransfers,
                        enabled = state.ready && !state.failed && !saving)
                }
            } } }
            if (state.failed) item { Text("Nie udało się odczytać ustawień Odjazdów.", color = MaterialTheme.colorScheme.error) }
            settingsError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (state.preferences.destination == null && !state.failed) item { Text("Wybierz przystanek docelowy, aby sprawdzić najbliższe połączenia ze szkoły.") }
            item { Button(onClick = { visibleCount = 3; viewModel.loadJourneys(force = true, minimumCount = 3) }, enabled = !loading && !moreLoading && state.ready && !state.failed && state.preferences.destination != null) { Text("Odśwież") } }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Szukam połączeń…", Modifier.padding(top = 8.dp)) }
            if (error != null) item { Text(error!! + if (matching != null) " Wyświetlam zapisane wyniki; rozkład mógł się zmienić." else "", color = MaterialTheme.colorScheme.error) }
            if (matching?.partialFailure == true) item {
                Text("Nie udało się sprawdzić części stanowisk. Lista może być niepełna; odśwież wyniki, aby ponowić.", color = MaterialTheme.colorScheme.error)
            }
            matching?.let { data -> item {
                Text("Ostatnio sprawdzono: ${formatTransitTime(data.fetchedAt, nowMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            if (!loading && error == null && matching != null && journeys.isEmpty()) item {
                Text("Nie znaleziono połączenia do wybranego przystanku w sprawdzonym przedziale. Odśwież wyniki lub wybierz inny cel.")
            }
            itemsIndexed(journeys.take(visibleCount), key = { _, trip -> trip.key }) { index, trip ->
                JourneyCard(trip, nowMs, if (index == 0) "Najbliższy przyjazd" else "Kolejny przyjazd")
            }
            if (journeys.size > visibleCount || matching?.nextWhenMs != null) item {
                TextButton(onClick = {
                    visibleCount = minOf(visibleCount, journeys.size) + 3
                    if (journeys.size < visibleCount && matching?.nextWhenMs != null) viewModel.loadJourneys(more = true, minimumCount = visibleCount)
                }, enabled = !loading && !moreLoading, modifier = Modifier.fillMaxWidth()) { Text(if (moreLoading) "Wczytywanie…" else "Pokaż więcej") }
            }
            item {
                val context = LocalContext.current
                TextButton(onClick = { SafeUrls.open(context, "https://www.openstreetmap.org/") }) {
                    Text("Trasy piesze: © OpenStreetMap · FOSSGIS", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (picker != 0) StopPicker(viewModel, origin = picker == 2) { picker = 0 }

}

@Composable
internal fun JourneyCard(trip: TransitJourney, nowMs: Long, label: String, onClick: (() -> Unit)? = null) {
    val first = trip.rides.first()
    ElektronCard(onClick = onClick) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(first.fromName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(if (trip.walkAvailable) "Dojście ok. ${TransitTimes.duration(trip.walkMinutes.toLong())}" else if (trip.walkPending) "Sprawdzam dojście…" else "Dojście niedostępne", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        RideDirection(first.line, first.direction)
        Text("${formatTransitTime(first.departureMs, nowMs)} · ${TransitTimes.until(trip.departureMs, nowMs)}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        if (trip.transfers > 0) {
            val second = trip.rides[1]
            Text("Przesiadka: ${first.toName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (first.toName != second.fromName) Text("Następny przystanek: ${second.fromName}", color = MaterialTheme.colorScheme.onSurface)
            RideDirection(second.line, second.direction)
            Text("Odjazd ${formatTransitTime(second.departureMs, nowMs)}", color = MaterialTheme.colorScheme.onSurface)
        }
        Text("Przyjazd ${formatTransitTime(trip.arrivalMs, nowMs)} · ${trip.rides.last().toName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    } }
}

@Composable
private fun RideDirection(line: String, direction: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
            Text(line, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Text(direction, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}
