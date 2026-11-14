package pl.zse.bydgoszcz.elektron.presentation.transit

import pl.zse.bydgoszcz.elektron.domain.util.AppClock

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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
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
private val transitZone get() = ZoneId.systemDefault()
internal fun formatTransitTime(timeMs: Long, referenceMs: Long = AppClock.millis()): String {
    val time = Instant.ofEpochMilli(timeMs).atZone(transitZone)
    val today = Instant.ofEpochMilli(referenceMs).atZone(transitZone).toLocalDate()
    val prefix = when (time.toLocalDate()) {
        today -> ""
        today.plusDays(1) -> "jutro "
        else -> time.format(pl.zse.bydgoszcz.elektron.domain.model.DateTexts.DAY_MONTH) + " "
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
    // Przystanek przy szkole liczy się w obu kierunkach (do szkoły - gdzie wysiadasz).
    val originKey = state.preferences.originKey
    var visibleCount by rememberSaveable(commute.direction, state.preferences.destination?.key, originKey, state.preferences.allowTransfers) { mutableIntStateOf(3) }
    // Do szkoły z ustawieniem "Kursy na czas przed lekcją": ile wcześniejszych kursów pokazać nad pierwszym ("Pokaż wcześniejsze").
    var earlierCount by rememberSaveable(commute.direction, state.preferences.destination?.key, originKey, state.preferences.allowTransfers) { mutableIntStateOf(0) }
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
        TransitJourneys.upcoming(matching?.journeys.orEmpty(), nowMs, if (toSchool) null else state.preferences.strictOrigin,
            state.preferences.allowTransfers, live)
            .let { if (state.preferences.strictOrigin == null) TransitJourneys.walkable(it, nowMs, live) else it }
            // Ze szkoły od najbliższego odjazdu (z opóźnieniem na żywo) - dawniej według przyjazdu do celu, więc
            // kurs "za 14 min" bywał nad "za 11 min". Do szkoły lista już jest według odjazdu.
            .let { if (toSchool) it else it.sortedBy { trip -> pl.zse.bydgoszcz.elektron.domain.model.TransitLive.expected(trip, live) } }
    }
    // Po odrzuceniu kursów, na które nie da się dojść, lista bywa krótsza niż żądana - dociągamy
    // kolejne okno planera sami (z limitem, żeby nie pytać w kółko), zamiast pokazywać jeden kurs.
    val targetMs = if (toSchool) commute.targetMs else null
    // Do szkoły: domyślnie od najbliższego kursu; z ustawieniem "Kursy na czas przed lekcją" od kursu, który jest
    // na miejscu najbliżej tylu minut przed lekcją (wcześniejsze po "Pokaż wcześniejsze").
    val margin = state.preferences.arrivalMarginMinutes
    val anchor = remember(journeys, targetMs, live, margin) { if (toSchool) TransitJourneys.anchorToSchool(journeys, targetMs, live, margin) else 0 }
    val firstShown = (anchor - earlierCount).coerceAtLeast(0)
    val shown = journeys.subList(firstShown, minOf(journeys.size, anchor + visibleCount).coerceAtLeast(firstShown))
    // Dociąganie kolejnego okna planera (z limitem, żeby nie pytać w kółko): po odrzuceniu kursów, na które
    // nie da się dojść, lista bywa krótsza niż żądana, a do szkoły - musi sięgać do godziny lekcji.
    val coversLesson = !toSchool || targetMs == null || journeys.any { (it.schoolArrivalMs ?: it.arrivalMs) > targetMs }
    // Najwyżej kilka dociągnięć na wybór i tylko, gdy poprzednie coś dodało - dawniej przy stronach planera
    // bez pasującego kursu zakładka pytała bez końca, aż planer odmawiał ("Nie udało się sprawdzić połączeń").
    // Limit zeruje się z każdym nowym wyszukiwaniem (odświeżenie, wynik po 10 min, zmiana czasu).
    val generation by viewModel.searchGeneration.collectAsStateWithLifecycle()
    var autoLoads by remember(commute.direction, state.preferences.destination?.key, originKey, state.preferences.allowTransfers, generation) {
        mutableStateOf(TransitAutoLoad.State())
    }
    LaunchedEffect(visible, matching, journeys.size, visibleCount, anchor, coversLesson, loading, moreLoading) {
        val data = matching ?: return@LaunchedEffect
        val end = if (toSchool) TransitCommute.searchEndMs(targetMs, data.searchFromMs ?: data.fetchedAt) else null
        if (visible && !loading && !moreLoading && TransitAutoLoad.shouldLoad(autoLoads, data.complete, data.journeys.size, data.nextWhenMs, end,
                needMore = journeys.size < anchor + visibleCount || !coversLesson)) {
            autoLoads = autoLoads.after(data.journeys.size)
            viewModel.loadJourneys(more = true, minimumCount = data.journeys.size + 3)
        }
    }
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
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    },
        containerColor = MaterialTheme.colorScheme.background) { padding ->
        val canShowMore = journeys.size > anchor + visibleCount || matching?.nextWhenMs != null
        val showMore: () -> Unit = {
            visibleCount = minOf(visibleCount, journeys.size - anchor) + 3
            if (journeys.size < anchor + visibleCount && matching?.nextWhenMs != null) viewModel.loadJourneys(more = true, minimumCount = anchor + visibleCount)
        }
        val canShowEarlier = firstShown > 0
        val showEarlier: () -> Unit = { earlierCount = anchor - firstShown + 3 }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // "Pokaż więcej" przyciskiem albo wyraźnym przeciągnięciem w górę na końcu listy (nie samym przewinięciem
        // do dołu - dawniej kursy dociągały się same, a krótki ruch już wczytywał kolejne).
        val pullAction by rememberUpdatedState(if (canShowMore && !loading && !moreLoading) showMore else null)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val pullThreshold = with(density) { 120.dp.toPx() }
        // Źródła danych przy dolnej krawędzi: widać je na końcu listy (pod "Pokaż więcej"), a przy krótkiej liście - na dole ekranu.
        var creditsHeight by remember { mutableIntStateOf(0) }
        val atEnd by remember { derivedStateOf { !listState.canScrollForward } }
        // Odświeżanie jak w innych zakładkach: przeciągnięcie listy w dół (bez przycisku na pasku).
        var pulled by remember { mutableStateOf(false) }
        LaunchedEffect(loading) { if (!loading) pulled = false }
        // Nie ma czego odświeżać (np. bez przystanku): krótki obrót i powrót - inaczej wskaźnik zostawał
        // przy krawędzi, bo stan odświeżania się nie zmieniał.
        var bounce by remember { mutableStateOf(false) }
        LaunchedEffect(bounce) { if (bounce) { kotlinx.coroutines.delay(350); bounce = false } }
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = (pulled && loading) || bounce,
            onRefresh = { if (canRefresh) { pulled = true; visibleCount = 3; viewModel.loadJourneys(force = true, minimumCount = 3) } else bounce = true },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        LazyColumn(Modifier.fillMaxSize().pullUpAtEnd(listState, pullThreshold) { pullAction?.invoke() },
            state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                bottom = 16.dp + with(density) { creditsHeight.toDp() }),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "direction") {
                DirectionSwitch(commute.direction, enabled = !saving) { viewModel.setDirection(it) }
            }
            item(key = "route") {
                val homeName = state.preferences.destination?.name
                val schoolName = state.preferences.preferredOrigin?.name
                // Twój przystanek i przystanek przy szkole ("Szkoła" - najbliższe przystanki); kolejność wg kierunku.
                val home = RouteStop("Twój przystanek", homeName, "Wybierz swój przystanek",
                    onClick = { viewModel.query.value = ""; picker = 1 },
                    onClear = if (homeName != null) viewModel::clear else null, clearLabel = "Usuń swój przystanek")
                val school = RouteStop("Szkoła", schoolName, "Szkoła",
                    onClick = { viewModel.query.value = ""; picker = 2 },
                    onClear = if (schoolName != null) ({ viewModel.selectOrigin(null) }) else null,
                    clearLabel = "Wróć do najbliższych przystanków szkoły", placeholderIsValue = true)
                androidx.compose.foundation.layout.Box(Modifier.tourTarget("transit:route")) {
                    RouteCard(from = if (toSchool) home else school, to = if (toSchool) school else home, enabled = editable)
                }
            }
            matching?.let { data -> item(key = "checked") {
                Text("Sprawdzono ${formatTransitTime(data.fetchedAt, nowMs)}" + (if (state.preferences.allowTransfers) " · z przesiadkami" else "") +
                    if (live.isNotEmpty()) " · opóźnienia na żywo" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp))
            } }
            if (state.failed) item { Text("Nie udało się odczytać ustawień Przejazdów.", color = MaterialTheme.colorScheme.error) }
            settingsError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (loading && !pulled) item(key = "loading") {
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
            // Wcześniejsze kursy - mniej widoczny napis.
            if (canShowEarlier) item(key = "earlier") {
                TextButton(onClick = showEarlier, modifier = Modifier.fillMaxWidth().animateItem()) {
                    Text("Pokaż wcześniejsze", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(shown, key = { _, trip -> trip.key }) { _, trip ->
                Box(Modifier.animateItem()) {
                    // Dotknięcie: gdzie jest teraz pojazd (gdy BUSearch go śledzi).
                    JourneyCard(trip, nowMs, onClick = { viewModel.openVehicle(trip) }, live = live[trip.key], lessonStartMs = targetMs,
                        onClickLabel = "Pokaż, gdzie jest pojazd", showDeparture = !toSchool && state.preferences.strictOrigin == null)
                }
            }
            // Jak w Ogłoszeniach: wypełniony przycisk na środku, w trakcie wczytywania kółko z opisem.
            if (canShowMore) item(key = "more") {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp).animateItem(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (moreLoading) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text("Wczytuję kolejne kursy…", Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else FilledTonalButton(onClick = showMore, enabled = !loading) { Text("Pokaż więcej") }
                }
            }
        }
        if (state.preferences.destination != null) {
            val creditsAlpha by androidx.compose.animation.core.animateFloatAsState(if (atEnd) 1f else 0f, label = "credits")
            TransitCredits(enabled = atEnd, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                .onSizeChanged { creditsHeight = it.height }.graphicsLayer { alpha = creditsAlpha })
        }
        }
    }
    if (picker != 0) StopPicker(viewModel, origin = picker == 2) { picker = 0 }
    VehicleSheet(viewModel)

}

/** Do szkoły / Ze szkoły (automatycznie według pory; ręczny wybór bez dodatkowego napisu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionSwitch(direction: TransitDirection, enabled: Boolean, onChange: (TransitDirection) -> Unit) {
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
    }
}

/** Punkt trasy: etykieta, nazwa (null - [placeholder]), wybór i usunięcie. */
private class RouteStop(val label: String, val name: String?, val placeholder: String, val onClick: () -> Unit,
    val onClear: (() -> Unit)?, val clearLabel: String, val placeholderIsValue: Boolean = false)

/** Trasa jak w planerach: skąd i dokąd w dwóch wierszach; oba punkty można zmienić. */
@Composable
private fun RouteCard(from: RouteStop, to: RouteStop, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    ElektronCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            RoutePoint("Skąd", from, enabled)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
            RoutePoint("Dokąd", to, enabled)
        }
    }
}

@Composable
private fun RoutePoint(label: String, stop: RouteStop, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val placeholder = stop.name == null && !stop.placeholderIsValue
    Row(Modifier.fillMaxWidth().pressable(enabled = enabled, onClickLabel = "Zmień: ${stop.label}", onClick = stop.onClick)
        .heightIn(min = 56.dp).padding(start = 16.dp, end = if (stop.onClear != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(stop.name ?: stop.placeholder, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = if (placeholder) colors.onSurfaceVariant else colors.onSurface)
        }
        stop.onClear?.let { clear -> IconButton(onClick = clear, enabled = enabled) { Icon(Icons.Filled.Close, stop.clearLabel) } }
    }
}

/**
 * Połączenie w trzech wierszach: linia i przystanek, na którym wysiadasz (z "za ile"), skąd
 * odjeżdża (z godziną i opóźnieniem na żywo) oraz jedna najważniejsza informacja - do szkoły:
 * o której będziesz na przystanku przy szkole i ile zapasu do lekcji (z dojściem), ze szkoły: kiedy wyjść.
 */
@Composable
internal fun JourneyCard(trip: TransitJourney, nowMs: Long, onClick: (() -> Unit)? = null,
    live: LiveStatus? = null, lessonStartMs: Long? = null, onClickLabel: String? = null,
    /** "Odjazd: przystanek" - tylko przy "Szkoła" (kilka przystanków); przy jednym wybranym to zbędne powtórzenie. */
    showDeparture: Boolean = true) {
    val first = trip.rides.first()
    val colors = MaterialTheme.colorScheme
    val toSchool = trip.schoolArrivalMs != null
    val departure = live?.expectedMs ?: trip.departureMs
    val shift = departure - trip.departureMs
    ElektronCard(onClick = onClick, onClickLabel = onClickLabel) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { RideTo(first.line, first.toName) }
            Spacer(Modifier.width(12.dp))
            Text(TransitTimes.until(departure, nowMs), style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = colors.primary, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // "Odjazd: Rycerska" zamiast "z Rycerska" (nazw przystanków się nie odmienia).
            if (showDeparture) Text("Odjazd: ${first.fromName}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            else Spacer(Modifier.weight(1f))
            live?.let { LiveChip(it); Spacer(Modifier.width(6.dp)) }
            Text(formatTransitTime(departure, nowMs), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
        }
        // Przesiadka pod odjazdem - godziny po kolei.
        if (trip.transfers > 0) {
            val second = trip.rides[1]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { RideTo(second.line, second.toName) }
                Text(formatTransitTime(second.departureMs + shift.coerceAtLeast(0), nowMs), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
            }
        }
        val atSchool = trip.schoolArrivalMs
        if (atSchool != null) {
            // Godzina na przystanku (wysiadka), a zapas - do lekcji, razem z dojściem do szkoły.
            val atStop = trip.rides.last().arrivalMs + shift
            val slack = lessonStartMs?.let { pl.zse.bydgoszcz.elektron.domain.model.TransitCommute.slackMinutes(atSchool + shift, it) }
            val text = "Na przystanku ${if (shift != 0L) "ok. " else ""}${formatTransitTime(atStop, nowMs)}" + when {
                slack == null -> ""
                slack < 0 -> " · spóźnienie ${TransitTimes.duration(-slack)}"
                slack == 0L -> " · na styk"
                else -> " · zapas ${TransitTimes.duration(slack)}"
            }
            // Bez czerwieni (odstawała od reszty karty): spóźnienie pogrubione w kolorze tekstu.
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
                fontWeight = if (slack != null && slack < 0) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
        }
    } }
}

/** Stan na żywo: na czas, opóźnienie albo odjazd przed czasem - stonowane kolory, jak reszta karty. */
@Composable
private fun LiveChip(status: LiveStatus) {
    val colors = MaterialTheme.colorScheme
    val text = when {
        status.delayMinutes == 0 -> "na czas"
        status.delayMinutes < 0 -> "${-status.delayMinutes} min wcześniej"
        else -> "+${status.delayMinutes} min"
    }
    val description = when {
        status.delayMinutes < 0 -> "Na żywo: odjedzie ${-status.delayMinutes} min przed czasem"
        status.delayMinutes == 0 -> "Na żywo: na czas"
        else -> "Na żywo: opóźnienie ${status.delayMinutes} min"
    }
    Surface(color = colors.secondaryContainer, shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(colors.onSecondaryContainer))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = colors.onSecondaryContainer, maxLines = 1)
        }
    }
}

private val WORD_HYPHEN = Regex("(?<=\\p{L})-(?=\\p{L})")

/** Linia i przystanek, na którym wysiadasz (nie kierunek pojazdu - ten bywa daleko za celem). */
@Composable
private fun RideTo(line: String, stop: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
            Text(line, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        // Miejsce na złamanie po łączniku w nazwie ("Skłodowskiej-|Curie") - przy dużej czcionce dawniej "Skłodowskiej-Cur / ie".
        Text(stop.replace(WORD_HYPHEN, "-\u200B"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/**
 * Źródła danych Przejazdów: kursy, opóźnienia i pozycje pojazdów z BUSearch (autor: Szymon Czaja), mapa
 * z OpenStreetMap. Dotknięcie otwiera stronę źródła.
 */
@Composable
private fun TransitCredits(enabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Połączenia i opóźnienia na żywo: BUSearch (Szymon Czaja)", style = style, color = color,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.pressable(enabled = enabled, onClickLabel = "Otwórz stronę BUSearch") { SafeUrls.open(context, "https://busearch.pl/bydgoszcz") }
                .padding(vertical = 4.dp, horizontal = 8.dp))
        Text("Mapa: © OpenStreetMap", style = style, color = color,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.pressable(enabled = enabled, onClickLabel = "Otwórz informacje o OpenStreetMap") { SafeUrls.open(context, "https://www.openstreetmap.org/copyright") }
                .padding(vertical = 4.dp, horizontal = 8.dp))
    }
}

/**
 * Wyraźne przeciągnięcie w górę na końcu listy kursów wczytuje kolejne (próg [thresholdPx], wyższy niż dawniej -
 * krótki ruch nie wystarcza). Tylko gest zaczęty przy końcu listy: ruch, który przewijał listę, nie wczytuje.
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
                if (state.canScrollForward) break
                pulled += change.previousPosition.y - change.position.y
                if (pulled >= thresholdPx) { onPull(); break }
            }
        }
    }
