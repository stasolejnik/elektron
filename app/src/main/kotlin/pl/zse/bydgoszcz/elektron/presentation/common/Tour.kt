package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.presentation.navigation.ElektronRoutes

/**
 * Krok przewodnika: [target] - element zgłoszony przez [tourTarget] (null - dymek na środku),
 * [route] - zakładka otwierana przed krokiem, [openMore] - rozwinięty panel "Więcej".
 */
data class TourStep(
    val target: String?,
    val route: String?,
    val title: String,
    val text: String,
    val openMore: Boolean = false
)

/** Kroki przewodnika po prawdziwej aplikacji. Bez "Więcej" i Przejazdów - bez ich kroków. */
fun tourSteps(moreEnabled: Boolean, transitVisible: Boolean, onBar: (String) -> Boolean = { true }): List<TourStep> = buildList {
    // Start i Zastępstwa wskazywane ikoną na pasku; schowane w "Więcej" albo ukryte - dymek bez wycięcia.
    fun nav(route: String) = "nav:$route".takeIf { onBar(route) }
    add(TourStep(nav(ElektronRoutes.DASHBOARD), ElektronRoutes.DASHBOARD, "Start",
        "Tu od razu widać, jaka lekcja trwa albo zaraz się zacznie, czy są zastępstwa i co nowego w ogłoszeniach. Przeciągnij ekran w dół, żeby odświeżyć."))
    add(TourStep("plan:mode", ElektronRoutes.TIMETABLE, "Plan lekcji",
        "Możesz oglądać plan dzień po dniu albo cały tydzień naraz. Zastępstwa są od razu wpisane w lekcje."))
    add(TourStep("plan:lesson", ElektronRoutes.TIMETABLE, "Notatki do lekcji",
        "Przytrzymaj lekcję, żeby zapisać notatkę, np. że będzie kartkówka. Zwykłe dotknięcie pokaże salę, nauczyciela i grupę."))
    add(TourStep("plan:notes", ElektronRoutes.TIMETABLE, "Moje notatki",
        "Tutaj są wszystkie Twoje notatki. Przypomnienie o nich włączysz w ustawieniach powiadomień."))
    add(TourStep(nav(ElektronRoutes.SUBSTITUTIONS), ElektronRoutes.SUBSTITUTIONS, "Zastępstwa",
        "Zmiany w planie na najbliższe dni, tylko dla Twojej klasy i grup. Gdy pojawi się nowe zastępstwo, dostaniesz powiadomienie."))
    if (moreEnabled) add(TourStep("more:panel", null, "Więcej",
        "Tu są pozostałe zakładki. Przytrzymaj ikonę i przeciągnij ją na pasek, jeśli chcesz mieć ją zawsze pod ręką.", openMore = true))
    if (transitVisible) add(TourStep("transit:route", ElektronRoutes.TRANSIT, "Przejazdy",
        "Wybierz przystanek, z którego zwykle jeździsz, np. przy domu. Rano pokażemy autobusy i tramwaje do szkoły, a po lekcjach powrót."))
    add(TourStep("settings:class", ElektronRoutes.SETTINGS, "Klasa i grupy",
        "Tu zmienisz klasę albo grupy, np. z angielskiego czy WF-u. Plan pokazuje tylko lekcje Twoich grup."))
    add(TourStep("settings:notifications", ElektronRoutes.SETTINGS, "Powiadomienia",
        "Zastępstwa, ogłoszenia, przypomnienia o lekcjach i powiadomienie na żywo z czasem do końca lekcji. Włączasz tylko to, czego potrzebujesz."))
    add(TourStep("settings:look", ElektronRoutes.SETTINGS, "Wygląd",
        "Jasny albo ciemny motyw, kolor aplikacji i jedna z dwóch ikon na ekranie głównym."))
    add(TourStep("settings:widgets", ElektronRoutes.SETTINGS, "Widżety",
        "Lekcja, plan dnia, zastępstwa i przejazdy na ekranie głównym telefonu. Dodasz je stąd jednym dotknięciem."))
    add(TourStep("settings:about", ElektronRoutes.SETTINGS, "To wszystko",
        "Przewodnik obejrzysz jeszcze raz w O aplikacji. Tam też jest „Poleć aplikację”, gdyby ktoś z klasy chciał ją mieć."))
}

/** Stan przewodnika: kroki, bieżący krok i położenia wskazywanych elementów (w oknie). */
@Stable
class TourState {
    var active by mutableStateOf(false)
        private set
    var index by mutableIntStateOf(0)
        private set
    var steps: List<TourStep> by mutableStateOf(emptyList())
        private set
    /** Kroki budowane przez gospodarza (zależą od ustawień pasku i Przejazdów). */
    var stepsProvider: () -> List<TourStep> = { emptyList() }
    var onFinished: () -> Unit = {}
    // Jeden klucz może mieć kilka egzemplarzy (np. strony planu w pagerze) - wybierany ten na ekranie.
    // Zwykła mapa z LayoutCoordinates (nie stan): poza przewodnikiem przewijanie niczego nie zapisuje,
    // a nakładka czyta położenia w każdej klatce, gdy przewodnik trwa.
    internal val targets = HashMap<Pair<String, Any>, androidx.compose.ui.layout.LayoutCoordinates>()

    val step: TourStep? get() = if (active) steps.getOrNull(index) else null
    /** Element wskazywany teraz (do przewinięcia list). */
    val currentTarget: String? get() = step?.target

    /** Start od [from] (po obróceniu ekranu - od zapisanego kroku). */
    fun start(from: Int = 0) {
        steps = stepsProvider()
        index = from.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
        active = steps.isNotEmpty()
    }

    fun next() { if (index < steps.lastIndex) index++ else finish() }

    fun finish() {
        if (!active) return
        active = false
        onFinished()
    }

    /** Położenie [key] najbardziej widoczne w [screen] (null - brak na ekranie). */
    fun rectFor(key: String, screen: Rect): Rect? =
        targets.entries.filter { it.key.first == key && it.value.isAttached }
            .map { it.value.boundsInWindow() }
            .map { it to it.intersect(screen) }
            .filter { (_, visible) -> visible.width > 1f && visible.height > 1f }
            .maxByOrNull { (_, visible) -> visible.width * visible.height }?.first
}

val LocalTour = staticCompositionLocalOf<TourState?> { null }

/** Element, który przewodnik może podświetlić. */
fun Modifier.tourTarget(key: String): Modifier = composed {
    val tour = LocalTour.current ?: return@composed this
    val token = remember { Any() }
    androidx.compose.runtime.DisposableEffect(tour, key) { onDispose { tour.targets.remove(key to token) } }
    onGloballyPositioned { tour.targets[key to token] = it }
}

/** Wycięcie: prostokąt i zaokrąglenie (0 - zaokrąglony prostokąt, 1 - koło). */
private data class Hole(val rect: Rect, val round: Float)

/** Położenia z ostatniej klatki (zwykłe pola - zapis w trakcie rysowania nie wywołuje ponownej kompozycji). */
private class TourGeometry {
    var origin = Offset.Zero
    var width = 0f
    var height = 0f
    var from: Hole? = null
    var fromY: Float? = null
    var last: Hole? = null
    var lastY: Float? = null
    /** Krok, dla którego zapamiętano punkt startowy, i krok, którego przejście już trwa. */
    var stepIndex = -1
    var animIndex = -1
    /** Czas klatki - odczyt stanu odświeżający nakładkę w każdej klatce. */
    var frameTime = 0L
    val screen get() = Rect(origin, androidx.compose.ui.geometry.Size(width, height))
}

/**
 * Nakładka przewodnika: przyciemnienie z wycięciem wokół wskazanego elementu i dymek ze strzałką.
 * Wycięcie jest "przyczepione" do elementu - przy zmianie kroku przechodzi do niego w 300 ms, a potem
 * jedzie razem z nim (przesuwanie zakładki, przewijanie listy). Dymek liczy położenie z tego samego
 * wycięcia w każdej klatce, więc nie zostaje w tyle. Pozycje są czytane przy rysowaniu i układaniu,
 * bez ponownej kompozycji w każdej klatce. Zatrzymuje dotyk; Wstecz kończy przewodnik.
 */
@Composable
fun TourOverlay(tour: TourState) {
    val step = tour.step ?: return
    BackHandler { tour.finish() }
    val density = LocalDensity.current
    val pad = with(density) { 6.dp.toPx() }
    val roundPad = with(density) { 3.dp.toPx() }
    val corner = with(density) { 20.dp.toPx() }
    val gap = with(density) { 12.dp.toPx() }
    val roundTolerance = with(density) { 12.dp.toPx() }
    val stroke = with(density) { 1.5.dp.toPx() }
    val safe = WindowInsets.safeDrawing
    val geo = remember { TourGeometry() }
    val progress = remember { Animatable(1f) }
    val reveal = remember { Animatable(0f) }
    val scrimIn = remember { Animatable(0f) }
    var missing by remember { mutableStateOf(false) }
    var below by remember { mutableStateOf(false) }
    var cardHeight by remember { mutableIntStateOf(0) }
    // Położenia elementów nie są stanem - nakładka odświeża się w każdej klatce, póki przewodnik trwa.
    var tick by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) androidx.compose.runtime.withFrameNanos { tick = it } }

    fun targetHole(): Hole? {
        val key = tour.step?.target ?: return null
        val local = tour.rectFor(key, geo.screen)?.translate(-geo.origin) ?: return null
        return if (kotlin.math.abs(local.width - local.height) < roundTolerance)
            // Koło ciasno wokół ikony - większe przecinało podpis pod ikoną na pasku.
            Hole(Rect(local.center, maxOf(local.width, local.height) / 2 + roundPad), 1f)
        else Hole(local.inflate(pad), 0f)
    }
    fun cardYFor(hole: Hole?, under: Boolean): Float {
        val top = safe.getTop(density) + gap
        val bottom = geo.height - safe.getBottom(density) - gap
        val y = when {
            hole == null -> (geo.height - cardHeight) / 2
            under -> hole.rect.bottom + gap
            else -> hole.rect.top - gap - cardHeight
        }
        return y.coerceIn(top, (bottom - cardHeight).coerceAtLeast(top))
    }
    /** Wycięcie i położenie dymku w tej klatce (z zapamiętaniem na początek następnego kroku). */
    fun frame(): Pair<Hole?, Float> {
        // Do startu animacji nowego kroku - punkt startowy (bez przeskoku w pierwszej klatce).
        // progress.value czytane zawsze - inaczej rysowanie nie odświeżałoby się, gdy animacja ruszy.
        val raw = progress.value
        geo.frameTime = tick
        val p = if (geo.animIndex == geo.stepIndex) raw else 0f
        val center = Hole(Rect(Offset(geo.width / 2, geo.height / 2), 0f), 0f)
        val to = targetHole() ?: if (missing) center else null
        val from = geo.from
        val hole = when {
            to == null -> from
            from == null -> to
            else -> Hole(androidx.compose.ui.geometry.lerp(from.rect, to.rect, p), from.round + (to.round - from.round) * p)
        }
        val toY = if (to == null) geo.fromY ?: cardYFor(null, false) else cardYFor(to.takeUnless { missing }, below)
        val y = geo.fromY?.let { it + (toY - it) * p } ?: toY
        geo.last = hole
        geo.lastY = y
        return hole to y
    }

    // Punkt startowy zapamiętany w chwili zmiany kroku - przed narysowaniem pierwszej klatki z nowym celem.
    if (geo.stepIndex != tour.index) {
        geo.from = geo.last
        geo.fromY = geo.lastY
        geo.stepIndex = tour.index
    }
    LaunchedEffect(Unit) { scrimIn.animateTo(1f, tween(260)) }
    LaunchedEffect(tour.index) {
        missing = false
        progress.snapTo(0f)
        geo.animIndex = tour.index
        // Zakładka może dopiero wjeżdżać - czekamy, aż element pojawi się na ekranie (najwyżej 1 s).
        var waited = 0
        while (step.target != null && targetHole() == null && waited < 1000) { delay(16); waited += 16 }
        val found = targetHole()
        missing = found == null
        below = found != null && found.rect.center.y <= geo.height / 2
        if (reveal.value < 1f) launch { reveal.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
        progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .onGloballyPositioned { geo.origin = it.positionInWindow() }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
    ) {
        geo.width = constraints.maxWidth.toFloat()
        geo.height = constraints.maxHeight.toFloat()
        val ring = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.7f * scrimIn.value))
            val hole = frame().first ?: return@Canvas
            val r = hole.rect
            if (r.width < 1f || reveal.value <= 0f) return@Canvas
            val radius = CornerRadius(minOf(r.height / 2, corner + (r.height / 2 - corner) * hole.round))
            drawRoundRect(Color.Black, r.topLeft, r.size, radius, alpha = reveal.value, blendMode = BlendMode.DstOut)
            drawRoundRect(ring, r.topLeft, r.size, radius, style = Stroke(stroke), alpha = 0.7f * reveal.value)
        }
        Box(Modifier.fillMaxWidth()
            .offset { IntOffset(0, frame().second.toInt()) }
            .graphicsLayer { alpha = if (cardHeight == 0) 0f else reveal.value }
            .onSizeChanged { cardHeight = it.height }, contentAlignment = Alignment.TopCenter) {
            TourCard(tour, step, tour.index, below = below && !missing, above = !below && !missing,
                caretX = { geo.last?.rect?.center?.x })
        }
    }
}

@Composable
private fun TourCard(tour: TourState, step: TourStep, index: Int, below: Boolean, above: Boolean, caretX: () -> Float?) {
    val last = index == tour.steps.lastIndex
    val colors = MaterialTheme.colorScheme
    val bubble = colors.primaryContainer
    val density = LocalDensity.current
    val left = remember { floatArrayOf(0f, 0f) } // x w oknie i szerokość - bez stanu, czytane przy rysowaniu
    val caretW = with(density) { 18.dp.toPx() }
    val caretH = with(density) { 9.dp.toPx() }
    val inset = with(density) { 28.dp.toPx() }
    // Strzałka nad/pod dymkiem, skierowana na środek elementu (w granicach zaokrągleń dymku).
    fun androidx.compose.ui.graphics.drawscope.DrawScope.caret(up: Boolean) {
        val x = caretX() ?: return
        val c = (x - left[0]).coerceIn(inset, (left[1] - inset).coerceAtLeast(inset))
        drawPath(androidx.compose.ui.graphics.Path().apply {
            if (up) { moveTo(c - caretW / 2, caretH); lineTo(c, 0f); lineTo(c + caretW / 2, caretH) }
            else { moveTo(c - caretW / 2, 0f); lineTo(c, caretH); lineTo(c + caretW / 2, 0f) }
            close()
        }, bubble)
    }
    Column(
        Modifier.padding(horizontal = 16.dp).widthIn(max = 440.dp).fillMaxWidth()
            .onGloballyPositioned { left[0] = it.positionInWindow().x; left[1] = it.size.width.toFloat() }
    ) {
        Canvas(Modifier.fillMaxWidth().height(9.dp)) { if (below) caret(up = true) }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = bubble, shadowElevation = 10.dp) {
            Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(step.title, style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer,
                    modifier = Modifier.padding(end = 8.dp))
                Spacer(Modifier.height(4.dp))
                Text(step.text, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(end = 8.dp))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TourDots(index, tour.steps.size, colors.onPrimaryContainer)
                    Spacer(Modifier.weight(1f))
                    if (!last) TextButton(onClick = tour::finish,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer)) { Text("Pomiń") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = tour::next) { Text(if (last) "Gotowe" else "Dalej") }
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(9.dp)) { if (above) caret(up = false) }
    }
}

/** Postęp jako kropki: przebyte i bieżąca wyraźne, kolejne przygaszone. */
@Composable
private fun TourDots(index: Int, count: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = "Krok ${index + 1} z $count" }) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == index) 14.dp else 5.dp, tween(240), label = "tourDot")
            Box(Modifier.size(width = w, height = 5.dp).clip(CircleShape)
                .background(color.copy(alpha = if (i <= index) 0.9f else 0.3f)))
        }
    }
}
