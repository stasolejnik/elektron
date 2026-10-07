package pl.zse.bydgoszcz.elektron.presentation.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.domain.model.TransitJourneys
import pl.zse.bydgoszcz.elektron.presentation.common.*
import pl.zse.bydgoszcz.elektron.presentation.transit.TransitViewModel
import pl.zse.bydgoszcz.elektron.presentation.transit.JourneyCard
import pl.zse.bydgoszcz.elektron.presentation.transit.formatTransitTime

@Composable
internal fun DashboardDeparture(viewModel: TransitViewModel, eligible: Boolean, onOpen: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    if (!eligible || !settings.ready || settings.failed || !settings.preferences.showOnDashboard) return
    var showDetails by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val openDetails: () -> Unit = { if (settings.preferences.visible) onOpen() else showDetails = true }
    if (showDetails) androidx.compose.ui.window.Dialog(onDismissRequest = { showDetails = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        pl.zse.bydgoszcz.elektron.presentation.transit.TransitScreen(viewModel, onClose = { showDetails = false })
    }
    SectionTitle("Najbliższy odjazd", Icons.Filled.DirectionsTransit)
    val prefs = settings.preferences
    if (prefs.destination == null) {
        ElektronCard(onClick = openDetails) {
            Text("Wybierz przystanek docelowy w ustawieniach lub dotknij tutaj, aby sprawdzić połączenie ze szkoły.", Modifier.padding(16.dp))
        }
        return
    }
    val visible = LocalScreenVisible.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(viewModel, visible, prefs.destination.key, prefs.preferredOrigin?.key, prefs.allowTransfers, owner) {
        val consumer = Any()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && visible) viewModel.startJourneySession(consumer)
            if (event == Lifecycle.Event.ON_STOP) viewModel.stopJourneySession(consumer)
        }
        owner.lifecycle.addObserver(observer)
        if (visible && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.startJourneySession(consumer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.stopJourneySession(consumer) }
    }
    val tick by rememberNow(60_000L)
    val now = remember(tick) { System.currentTimeMillis() }
    // Foreground only; the shared minute cache avoids a second request from Departures.
    LaunchedEffect(visible, tick, prefs.destination.key, prefs.preferredOrigin?.key, prefs.allowTransfers) {
        if (visible && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.refreshJourneys()
    }
    val result by viewModel.journeys.collectAsStateWithLifecycle()
    val loading by viewModel.journeyLoading.collectAsStateWithLifecycle()
    val error by viewModel.journeyError.collectAsStateWithLifecycle()
    val matching = result?.takeIf { it.destinationKey == prefs.destination.key && it.originKey == prefs.preferredOrigin?.key && it.allowTransfers == prefs.allowTransfers }
    val trip = remember(matching, now, prefs.preferredOrigin, prefs.allowTransfers) {
        TransitJourneys.upcoming(matching?.journeys.orEmpty(), now, prefs.preferredOrigin, prefs.allowTransfers).minByOrNull { it.departureMs }
    }
    Column {
        when {
            trip != null -> JourneyCard(trip, now, "", openDetails)
            loading -> DelayedLoading { Text("Sprawdzam najbliższy odjazd…", Modifier.padding(16.dp)) }
            else -> ElektronCard(onClick = openDetails) {
                Text(if (error != null) "Nie udało się sprawdzić odjazdu. Dotknij tutaj, aby ponowić." else "Brak nadchodzących połączeń. Dotknij tutaj, aby sprawdzić więcej.", Modifier.padding(16.dp))
            }
        }
        matching?.let { Text("Ostatnio sprawdzono: ${formatTransitTime(it.fetchedAt, now)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        if (matching?.partialFailure == true) Text("Sprawdzono tylko część stanowisk. Lista może być niepełna.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (error != null && trip != null) Text("Nie udało się odświeżyć. Wyświetlam zapisany odjazd.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
