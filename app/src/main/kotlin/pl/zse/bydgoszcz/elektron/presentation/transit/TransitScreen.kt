package pl.zse.bydgoszcz.elektron.presentation.transit

import pl.zse.bydgoszcz.elektron.domain.util.AppClock

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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
internal fun formatTransitTime(timeMs: Long, referenceMs: Long = AppClock.millis()): String {
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
    val commute by viewModel.commute.collectAsStateWithLifecycle()
    val result by viewModel.journeys.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val loading by viewModel.journeyLoading.collectAsStateWithLifecycle()
    val moreLoading by viewModel.moreLoading.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableIntStateOf(0) }
    val toSchool = commute.toSchool
    val originKey = if (toSchool) null else state.preferences.preferredOrigin?.key
    var visibleCount by rememberSaveable(commute.direction, state.preferences.destination?.key, originKey, state.preferences.allowTransfers) { mutableIntStateOf(3) }
    val error by viewModel.journeyError.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val settingsError by viewModel.settingsError.collectAsStateWithLifecycle()
    val visible = LocalScreenVisible.current
    val requestedCount by rememberUpdatedState(visibleCount)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(visible, commute.direction, state.preferences.destination?.key, originKey, state.preferences.allowTransfers, owner) {
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
    LaunchedEffect(visible, tick, commute.direction, state.preferences.destination?.key, originKey, state.preferences.allowTransfers) {
        if (visible && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.refreshJourneys(requestedCount)
    }
    val nowMs = remember(tick) { AppClock.millis() }
    val matching = result?.takeIf { viewModel.matchesCurrent(it) }
    val journeys = remember(matching, nowMs, originKey, state.preferences.allowTransfers, live) {
        TransitJourneys.upcoming(matching?.journeys.orEmpty(), nowMs, if (toSchool) null else state.preferences.preferredOrigin,
            state.preferences.allowTransfers, live)
    }
    val targetMs = if (toSchool) commute.targetMs else null
    val lastOnTime = remember(journeys, targetMs, live) { TransitJourneys.lastOnTime(journeys, targetMs, live) }
    val editable = state.ready && !state.failed && !saving
    val canRefresh = !loading && !moreLoading && editable && state.preferences.destination != null
    // Planer bywa wolny (każde stanowisko to osobne zapytanie) - po kilku sekundach wyjaśnienie zamiast samego paska.
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { slow = false; if (loading) { kotlinx.coroutines.delay(6_000); slow = true } }
    // Zwarty pasek zamiast dużego tytułu: trasa i pierwsze połączenia mieszczą się na jednym ekranie.
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Przejazdy", modifier = androidx.compose.ui.Modifier.semantics { heading() }) },
            navigationIcon = { if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Zamknij Przejazdy") } },
            actions = {
                if (loading) CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { visibleCount = 3; viewModel.loadJourneys(force = true, minimumCount = 3) }, enabled = canRefresh) {
                    Icon(Icons.Filled.Refresh, "Odśwież połączenia")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    },
        // Źródła danych zawsze na dole ekranu, a nie w środku krótkiej listy.
        bottomBar = {
            val context = LocalContext.current
            Text(if (toSchool) "Połączenia i opóźnienia: BUSearch" else "Połączenia: BUSearch · trasy piesze: © OpenStreetMap · FOSSGIS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().pressable {
                    SafeUrls.open(context, if (toSchool) "https://busearch.pl/bydgoszcz" else "https://www.openstreetmap.org/copyright")
                }.padding(vertical = 8.dp))
        },
        containerColor = MaterialTheme.colorScheme.background) { padding ->
        // "Pokaż więcej" także gestem: przeciągnięcie w górę na końcu listy (jak przejście do
        // następnego dnia w planie) - nowym ruchem, nie rozpędem przewijania listy.
        val canShowMore = journeys.size > visibleCount || matching?.nextWhenMs != null
        val showMore: () -> Unit = {
            visibleCount = minOf(visibleCount, journeys.size) + 3
            if (journeys.size < visibleCount && matching?.nextWhenMs != null) viewModel.loadJourneys(more = true, minimumCount = visibleCount)
        }
        val pullAction by rememberUpdatedState(if (canShowMore && !loading && !moreLoading) showMore else null)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val pullThreshold = with(density) { 56.dp.toPx() }
        LazyColumn(Modifier.fillMaxSize().padding(padding).pullUpAtEnd(listState, pullThreshold) { pullAction?.invoke() },
            state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "direction") {
                DirectionSwitch(commute.direction, auto = commute.auto, enabled = !saving) { viewModel.setDirection(it) }
            }
            item(key = "route") {
                val home = state.preferences.destination?.name
                val schoolStop = state.preferences.preferredOrigin?.name
                RouteCard(
                    fromLabel = "Skąd",
                    from = if (toSchool) home else schoolStop,
                    fromPlaceholder = if (toSchool) "Wybierz swój przystanek" else "Przystanek najbliżej szkoły z odjazdem",
                    toLabel = "Dokąd",
                    to = if (toSchool) "Szkoła (Karłowicza 20)" else home,
                    toPlaceholder = "Wybierz swój przystanek",
                    enabled = editable,
                    onFrom = { viewModel.query.value = ""; picker = if (toSchool) 1 else 2 },
                    onClearFrom = if (toSchool) (if (home != null) viewModel::clear else null)
                        else if (schoolStop != null) ({ viewModel.selectOrigin(null) }) else null,
                    clearFromLabel = if (toSchool) "Usuń swój przystanek" else "Wróć do przystanku najbliżej szkoły",
                    onTo = if (toSchool) null else ({ viewModel.query.value = ""; picker = 1 }),
                    onClearTo = if (!toSchool && home != null) viewModel::clear else null
                )
            }
            if (toSchool && state.preferences.destination != null) item(key = "target") {
                TargetLine(commute.target, nowMs, journeys, targetMs, live)
            }
            matching?.let { data -> item(key = "checked") {
                Text("Sprawdzono ${formatTransitTime(data.fetchedAt, nowMs)}" + (if (state.preferences.allowTransfers) " · z przesiadkami" else "") +
                    if (live.isNotEmpty()) " · opóźnienia na żywo" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp))
            } }
            if (state.failed) item { Text("Nie udało się odczytać ustawień Przejazdów.", color = MaterialTheme.colorScheme.error) }
            settingsError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (state.preferences.destination == null && !state.failed) item {
                Text("Wybierz swój przystanek (np. przy domu). Rano pokażę dojazd do szkoły, po lekcjach powrót.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading) item(key = "loading") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (slow) "Planer odpowiada wolniej niż zwykle - sprawdzanie wszystkich przystanków może potrwać do minuty."
                        else "Szukam połączeń…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (error != null) item { Text(error!! + if (matching != null) " Wyświetlam zapisane wyniki; rozkład mógł się zmienić." else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (matching?.partialFailure == true) item {
                Text("Nie udało się sprawdzić części stanowisk. Lista może być niepełna; odśwież wyniki, aby ponowić.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (!loading && error == null && matching != null && journeys.isEmpty()) item {
                Text(if (toSchool) "Nie znaleziono połączenia z Twojego przystanku pod szkołę w sprawdzonym przedziale. Odśwież wyniki lub włącz przesiadki w ustawieniach."
                    else "Nie znaleziono połączenia do Twojego przystanku w sprawdzonym przedziale. Odśwież wyniki lub włącz przesiadki w ustawieniach.")
            }
            // Animacje: kolejne odjazdy ("Pokaż więcej", wyniki cząstkowe) pojawiają się płynnie,
            // a przycisk zjeżdża w dół zamiast przeskakiwać.
            itemsIndexed(journeys.take(visibleCount), key = { _, trip -> trip.key }) { index, trip ->
                Box(Modifier.animateItem()) {
                    val label = listOfNotNull(if (index == 0) "Najbliższy" else null,
                        if (trip === lastOnTime) "Ostatni, którym zdążysz" else null).joinToString(" · ")
                    JourneyCard(trip, nowMs, label, live = live[trip.key], lessonStartMs = targetMs)
                }
            }
            if (journeys.size > visibleCount || matching?.nextWhenMs != null) item(key = "more") {
                TextButton(onClick = showMore, enabled = !loading && !moreLoading, modifier = Modifier.fillMaxWidth().animateItem()) {
                    AnimatedVisibility(visible = moreLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    Text(if (moreLoading) "Wczytywanie…" else "Pokaż więcej")
                }
            }
        }
    }
    if (picker != 0) StopPicker(viewModel, origin = picker == 2) { picker = 0 }

}

/** Do szkoły / Ze szkoły. Automatyczny kierunek (połowa dnia lekcji) podpisany pod spodem. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionSwitch(direction: TransitDirection, auto: TransitDirection, enabled: Boolean, onChange: (TransitDirection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(TransitDirection.TO_SCHOOL to "Do szkoły", TransitDirection.FROM_SCHOOL to "Ze szkoły")
            options.forEachIndexed { i, (value, label) ->
                SegmentedButton(selected = direction == value, onClick = { onChange(value) }, enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    icon = { Icon(if (value == TransitDirection.TO_SCHOOL) Icons.Outlined.School else Icons.Outlined.Home, null,
                        Modifier.size(SegmentedButtonDefaults.IconSize)) }) { Text(label) }
            }
        }
        if (direction != auto) Text("Wybrane ręcznie. Automatycznie: do połowy dnia lekcji do szkoły, potem ze szkoły.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Do szkoły: na którą lekcję i ile kursów jeszcze zdąży. */
@Composable
private fun TargetLine(target: pl.zse.bydgoszcz.elektron.domain.model.TransitCommute.Target?, nowMs: Long,
    journeys: List<TransitJourney>, targetMs: Long?, live: Map<String, LiveStatus>) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.primaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.School, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (target == null || targetMs == null) {
                    Text("Brak lekcji w najbliższych dniach", style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer)
                    Text("Pokazuję najbliższe kursy pod szkołę.", style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer)
                } else {
                    Text("Na ${target.lesson.number}. lekcję o ${formatTransitTime(targetMs, nowMs)}",
                        style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer)
                    val onTime = journeys.count { trip -> trip.schoolArrivalMs?.let { it + TransitJourneys.delayMs(trip, live) <= targetMs } == true &&
                        TransitLive.expected(trip, live) > nowMs }
                    val wait = TransitTimes.until(targetMs, nowMs)
                    Text("Lekcja $wait" + if (journeys.isNotEmpty()) " · zdążysz ${onTime.kursy()}" else "",
                        style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer)
                }
            }
        }
    }
}

private fun Int.kursy() = when {
    this == 1 -> "1 kursem z listy"
    else -> "$this kursami z listy"
}

/**
 * Trasa jak w planerach: skąd i dokąd w dwóch wierszach. Do szkoły cel jest stały (szkoła),
 * ze szkoły - start to przystanek przy szkole (automatycznie najbliższy z odjazdem).
 */
@Composable
private fun RouteCard(
    fromLabel: String, from: String?, fromPlaceholder: String,
    toLabel: String, to: String?, toPlaceholder: String, enabled: Boolean,
    onFrom: () -> Unit, onClearFrom: (() -> Unit)?, clearFromLabel: String,
    onTo: (() -> Unit)?, onClearTo: (() -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    ElektronCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            RoutePoint(label = fromLabel, value = from ?: fromPlaceholder,
                placeholder = from == null, enabled = enabled, onClick = onFrom,
                trailing = onClearFrom?.let { clear -> { IconButton(onClick = clear, enabled = enabled) { Icon(Icons.Filled.Close, clearFromLabel) } } })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
            RoutePoint(label = toLabel, value = to ?: toPlaceholder,
                placeholder = to == null, enabled = enabled && onTo != null, onClick = onTo ?: {},
                trailing = onClearTo?.let { clear -> { IconButton(onClick = clear, enabled = enabled) { Icon(Icons.Filled.Close, "Usuń swój przystanek") } } })
        }
    }
}

@Composable
private fun RoutePoint(label: String, value: String, placeholder: Boolean, enabled: Boolean,
    onClick: () -> Unit, trailing: (@Composable () -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().pressable(enabled = enabled, onClickLabel = "Zmień: $label", onClick = onClick)
        .heightIn(min = 56.dp).padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = if (placeholder) colors.onSurfaceVariant else colors.onSurface)
        }
        trailing?.invoke()
    }
}

/**
 * Połączenie: linia i kierunek, przystanek, a po prawej duże "za ile" i godzina odjazdu (z opóźnieniem
 * na żywo, gdy jest). Ze szkoły - kiedy wyjść; do szkoły - kiedy będziesz w szkole i ile zapasu.
 */
@Composable
internal fun JourneyCard(trip: TransitJourney, nowMs: Long, label: String, onClick: (() -> Unit)? = null,
    live: LiveStatus? = null, lessonStartMs: Long? = null) {
    val first = trip.rides.first()
    val colors = MaterialTheme.colorScheme
    val toSchool = trip.schoolArrivalMs != null
    val departure = live?.expectedMs ?: trip.departureMs
    val shift = departure - trip.departureMs
    ElektronCard(onClick = onClick) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelMedium, color = colors.primary)
                RideDirection(first.line, first.direction)
                Text(first.fromName, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                if (!toSchool) LeaveLine(trip, departure, nowMs)
            }
            Spacer(Modifier.width(12.dp))
            // Najważniejsze "za ile" - duże; dokładna godzina pod spodem.
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(TransitTimes.until(departure, nowMs), style = MaterialTheme.typography.headlineSmall, color = colors.primary, maxLines = 1)
                Text(formatTransitTime(departure, nowMs), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, maxLines = 1)
                live?.let { LiveChip(it) }
            }
        }
        if (trip.transfers > 0) {
            val second = trip.rides[1]
            HorizontalDivider(color = colors.outlineVariant)
            Text("Przesiadka: ${first.toName}" + if (first.toName != second.fromName) " → ${second.fromName}" else "",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { RideDirection(second.line, second.direction) }
                Text(formatTransitTime(second.departureMs + shift.coerceAtLeast(0), nowMs), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            }
        }
        val atSchool = trip.schoolArrivalMs
        if (atSchool != null) {
            val at = atSchool + shift
            val slack = lessonStartMs?.let { pl.zse.bydgoszcz.elektron.domain.model.TransitCommute.slackMinutes(at, it) }
            val text = "W szkole ${if (shift != 0L) "ok. " else ""}${formatTransitTime(at, nowMs)}" + when {
                slack == null -> ""
                slack < 0 -> " · spóźnienie ${TransitTimes.duration(-slack)}"
                slack == 0L -> " · na styk"
                else -> " · zapas ${TransitTimes.duration(slack)}"
            }
            // Bez czerwieni (odstawała od reszty karty): spóźnienie pogrubione w kolorze tekstu.
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
                fontWeight = if (slack != null && slack < 0) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
            Text("Wysiadasz: ${trip.rides.last().toName}" + if (trip.walkMinutes > 0) " · dojście ok. ${TransitTimes.duration(trip.walkMinutes.toLong())}" else "",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        } else {
            Text("Przyjazd ${if (shift != 0L) "ok. " else ""}${formatTransitTime(trip.arrivalMs + shift.coerceAtLeast(0), nowMs)} · ${trip.rides.last().toName}",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    } }
}

/** Ze szkoły: kiedy wyjść (dojście i 2 min zapasu), a gdy czasu mało - pospiesz się. */
@Composable
private fun LeaveLine(trip: TransitJourney, departure: Long, nowMs: Long) {
    val colors = MaterialTheme.colorScheme
    if (!trip.walkAvailable) {
        Text(if (trip.walkPending) "Sprawdzam dojście…" else "Dojście niedostępne", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        return
    }
    val walk = "dojście ${TransitTimes.duration(trip.walkMinutes.toLong())}"
    val leave = trip.leaveSchoolMs(departure)
    val (text, urgent) = when {
        leave - nowMs > 60_000L -> "Wyjdź o ${formatTransitTime(leave, nowMs)} · $walk" to false
        leave >= nowMs - 60_000L -> "Wyjdź teraz · $walk" to true
        departure - trip.walkMinutes * 60_000L >= nowMs -> "Pospiesz się · $walk" to true
        else -> "Raczej nie zdążysz · $walk" to false
    }
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = if (urgent) colors.onSurface else colors.onSurfaceVariant,
        fontWeight = if (urgent) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
}

/** Stan na żywo: na czas, opóźnienie albo odjazd przed czasem - stonowane kolory, jak reszta karty. */
@Composable
private fun LiveChip(status: LiveStatus) {
    val colors = MaterialTheme.colorScheme
    val (text, container, content) = when {
        status.delayMinutes == 0 -> Triple("na czas", colors.primaryContainer, colors.onPrimaryContainer)
        status.delayMinutes < 0 -> Triple("${-status.delayMinutes} min wcześniej", colors.secondaryContainer, colors.onSecondaryContainer)
        else -> Triple("+${status.delayMinutes} min", colors.secondaryContainer, colors.onSecondaryContainer)
    }
    val description = when {
        status.delayMinutes < 0 -> "Na żywo: odjedzie ${-status.delayMinutes} min przed czasem"
        status.delayMinutes == 0 -> "Na żywo: na czas"
        else -> "Na żywo: opóźnienie ${status.delayMinutes} min"
    }
    Surface(color = container, shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(content))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
        }
    }
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

/**
 * Przeciągnięcie w górę na końcu listy odjazdów wczytuje kolejne. Tylko gest zaczęty przy końcu
 * listy (jak w planie): ruch, który przewijał listę, nie wywołuje wczytania po dojechaniu do dołu.
 * Czyta sam dotyk (bez zużywania), bo nadmiaru przewijania na końcu listy nie dostaje
 * nadrzędne nestedScroll - pochłania go efekt rozciągnięcia (overscroll).
 */
private fun Modifier.pullUpAtEnd(state: androidx.compose.foundation.lazy.LazyListState, thresholdPx: Float, onPull: () -> Unit) =
    pointerInput(state, thresholdPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (state.canScrollForward) return@awaitEachGesture
            var pulled = 0f
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (state.canScrollForward) continue
                pulled += change.previousPosition.y - change.position.y
                if (pulled >= thresholdPx) { onPull(); break }
            }
        }
    }
