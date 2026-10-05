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
import kotlin.math.ceil

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
fun TransitScreen(viewModel: TransitViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.settings.collectAsStateWithLifecycle()
    val result by viewModel.journeys.collectAsStateWithLifecycle()
    val loading by viewModel.journeyLoading.collectAsStateWithLifecycle()
    val moreLoading by viewModel.moreLoading.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableIntStateOf(0) }
    var visibleCount by rememberSaveable(state.preferences.destination?.key, state.preferences.preferredOrigin?.key, state.preferences.allowTransfers) { mutableIntStateOf(6) }
    val error by viewModel.journeyError.collectAsStateWithLifecycle()
    val visible = LocalScreenVisible.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(visible, state.preferences.visible, state.preferences.destination?.key, state.preferences.preferredOrigin?.key, state.preferences.allowTransfers, owner) {
        val active = visible && state.preferences.visible
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && active) viewModel.loadJourneys()
            if (event == Lifecycle.Event.ON_STOP) viewModel.cancelJourneys()
        }
        owner.lifecycle.addObserver(observer)
        if (active && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.loadJourneys()
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.cancelJourneys() }
    }
    val tick by rememberNow()
    val nowMs = remember(tick) { System.currentTimeMillis() }
    val matching = result?.takeIf { it.destinationKey == state.preferences.destination?.key && it.originKey == state.preferences.preferredOrigin?.key && it.allowTransfers == state.preferences.allowTransfers }
    val journeys = remember(matching, nowMs, state.preferences.preferredOrigin, state.preferences.allowTransfers) {
        TransitJourneys.rank(matching?.journeys.orEmpty(), nowMs, state.preferences.preferredOrigin, state.preferences.allowTransfers)
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = { LargeTitleBar("Odjazdy", scroll) },
        containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ElektronCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ze szkoły · Karłowicza 20", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.preferences.destination?.name ?: "Wybierz cel", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { viewModel.query.value = ""; picker = 1 }) { Text("Zmień przystanek docelowy") }
                HorizontalDivider()
                Text("Odjazd z przystanku", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.preferences.preferredOrigin?.name ?: "Najbliższy odpowiedni", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { viewModel.query.value = ""; picker = 2 }) { Text("Wybierz preferowany przystanek") }
            } } }
            item { Button(onClick = { visibleCount = 6; viewModel.loadJourneys(force = true) }, enabled = !loading && !moreLoading && state.preferences.visible) { Text("Odśwież") } }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Szukam połączeń…", Modifier.padding(top = 8.dp)) }
            if (error != null) item { Text(error!! + if (matching != null) " Wyświetlam zapisane wyniki; rozkład mógł się zmienić." else "", color = MaterialTheme.colorScheme.error) }
            matching?.let { data -> item {
                Text("Ostatnio sprawdzono: ${formatTransitTime(data.fetchedAt, nowMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            if (!loading && error == null && matching != null && journeys.isEmpty()) item {
                Text("Nie znaleziono osiągalnego połączenia do wybranego przystanku w sprawdzonym przedziale. Odśwież wyniki lub wybierz inny cel.")
            }
            itemsIndexed(journeys.take(visibleCount), key = { _, trip -> trip.key }) { index, trip ->
                JourneyCard(trip, nowMs, if (index == 0) "Polecany odjazd" else "Kolejny odjazd")
            }
            if (journeys.size > visibleCount || matching?.nextWhenMs != null) item {
                TextButton(onClick = {
                    visibleCount += 6
                    if (journeys.size < visibleCount && matching?.nextWhenMs != null) viewModel.loadJourneys(more = true)
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
private fun JourneyCard(trip: TransitJourney, nowMs: Long, label: String) {
    val first = trip.rides.first()
    val minutes = ceil((trip.departureMs - nowMs) / 60_000.0).toInt().coerceAtLeast(0)
    ElektronCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(first.fromName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(if (trip.walkAvailable) "Dojście ok. ${trip.walkMinutes} minut" else if (trip.walkPending) "Sprawdzam dojście…" else "Dojście niedostępne", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        RideDirection(first.line, first.direction)
        Text("${formatTransitTime(first.departureMs, nowMs)} · za $minutes min", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
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
