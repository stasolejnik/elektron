package pl.zse.bydgoszcz.elektron.presentation.dashboard

import pl.zse.bydgoszcz.elektron.domain.util.AppClock

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
import pl.zse.bydgoszcz.elektron.domain.model.TransitDirection
import pl.zse.bydgoszcz.elektron.domain.model.TransitLive
import pl.zse.bydgoszcz.elektron.presentation.common.*
import pl.zse.bydgoszcz.elektron.presentation.transit.TransitViewModel
import pl.zse.bydgoszcz.elektron.presentation.transit.JourneyCard
import pl.zse.bydgoszcz.elektron.presentation.transit.formatTransitTime

/**
 * Karta Przejazdów na stronie głównej: rano najbliższy dojazd do szkoły, po lekcjach najbliższy
 * odjazd ze szkoły. [direction] - kiedy karta ma się pojawić (TransitCommute.dashboardDirection);
 * pokazuje się, gdy Przejazdy szukają w tym samym kierunku (ręczny wybór w zakładce ma pierwszeństwo).
 */
@Composable
internal fun DashboardDeparture(viewModel: TransitViewModel, direction: TransitDirection?, onOpen: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val commute by viewModel.commute.collectAsStateWithLifecycle()
    if (direction == null || !settings.ready || settings.failed || !settings.preferences.showOnDashboard) return
    if (commute.direction != direction) return
    var showDetails by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val openDetails: () -> Unit = { if (settings.preferences.visible) onOpen() else showDetails = true }
    if (showDetails) androidx.compose.ui.window.Dialog(onDismissRequest = { showDetails = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        pl.zse.bydgoszcz.elektron.presentation.transit.TransitScreen(viewModel, onClose = { showDetails = false })
    }
    val toSchool = direction == TransitDirection.TO_SCHOOL
    SectionTitle(if (toSchool) "Dojazd do szkoły" else "Powrót ze szkoły", Icons.Filled.DirectionsTransit, Modifier.padding(bottom = 10.dp))
    val prefs = settings.preferences
    if (prefs.destination == null) {
        ElektronCard(onClick = openDetails) {
            Text("Wybierz swój przystanek w ustawieniach lub dotknij tutaj, aby sprawdzić dojazd i powrót.", Modifier.padding(16.dp))
        }
        return
    }
    val visible = LocalScreenVisible.current
    val owner = LocalLifecycleOwner.current
    val originKey = prefs.preferredOrigin?.key
    DisposableEffect(viewModel, visible, direction, prefs.destination.key, originKey, prefs.allowTransfers, owner) {
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
    val now = remember(tick) { AppClock.millis() }
    // Foreground only; the shared minute cache avoids a second request from Przejazdy.
    LaunchedEffect(visible, tick, direction, prefs.destination.key, originKey, prefs.allowTransfers) {
        if (visible && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.refreshJourneys()
    }
    val result by viewModel.journeys.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val loading by viewModel.journeyLoading.collectAsStateWithLifecycle()
    val error by viewModel.journeyError.collectAsStateWithLifecycle()
    val matching = result?.takeIf { viewModel.matchesCurrent(it) }
    val targetMs = if (toSchool) commute.targetMs else null
    val trip = remember(matching, now, originKey, prefs.allowTransfers, live, targetMs) {
        val upcoming = TransitJourneys.upcoming(matching?.journeys.orEmpty(), now, if (toSchool) null else prefs.preferredOrigin, prefs.allowTransfers, live)
        // Do szkoły: najbliższy kurs, który zdąży na lekcję (gdy żaden - najbliższy w ogóle).
        (if (targetMs != null) upcoming.filter { t -> t.schoolArrivalMs?.let { it + TransitJourneys.delayMs(t, live) <= targetMs } == true }
            .minByOrNull { TransitLive.expected(it, live) } else null)
            ?: upcoming.minByOrNull { TransitLive.expected(it, live) }
    }
    Column {
        when {
            trip != null -> JourneyCard(trip, now, "", openDetails, live = live[trip.key], lessonStartMs = targetMs)
            loading -> DelayedLoading { Text(if (toSchool) "Sprawdzam dojazd…" else "Sprawdzam najbliższy odjazd…", Modifier.padding(16.dp)) }
            else -> ElektronCard(onClick = openDetails) {
                Text(if (error != null) "Nie udało się sprawdzić połączeń. Dotknij tutaj, aby ponowić." else "Brak nadchodzących połączeń. Dotknij tutaj, aby sprawdzić więcej.", Modifier.padding(16.dp))
            }
        }
        matching?.let { Text("Ostatnio sprawdzono: ${formatTransitTime(it.fetchedAt, now)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        if (matching?.partialFailure == true) Text("Sprawdzono tylko część stanowisk. Lista może być niepełna.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (error != null && trip != null) Text("Nie udało się odświeżyć. Wyświetlam zapisane połączenie.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
