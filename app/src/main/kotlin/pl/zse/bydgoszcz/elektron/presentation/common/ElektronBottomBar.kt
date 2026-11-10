package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DirectionsTransit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import pl.zse.bydgoszcz.elektron.domain.model.MoreMenu
import pl.zse.bydgoszcz.elektron.domain.model.NavigationOrder
import pl.zse.bydgoszcz.elektron.presentation.navigation.ElektronRoutes
import kotlin.math.roundToInt

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

val bottomDestinations = listOf(
    BottomDestination(ElektronRoutes.DASHBOARD, "Strona główna", Icons.Outlined.Home, Icons.Filled.Home),
    BottomDestination(ElektronRoutes.TIMETABLE, "Plan", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    BottomDestination(ElektronRoutes.SUBSTITUTIONS, "Zastępstwa", Icons.Outlined.EventBusy, Icons.Filled.EventBusy),
    BottomDestination(ElektronRoutes.ANNOUNCEMENTS, "Ogłoszenia", Icons.Outlined.Campaign, Icons.Filled.Campaign),
    BottomDestination(ElektronRoutes.SETTINGS, "Ustawienia", Icons.Outlined.Settings, Icons.Filled.Settings)
)

private val transitDestination = BottomDestination(ElektronRoutes.TRANSIT, "Przejazdy", Icons.Outlined.DirectionsTransit, Icons.Filled.DirectionsTransit)
private val moreDestination = BottomDestination(MoreMenu.ROUTE, "Więcej", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz)

/** Przycisk albo zakładka po nazwie trasy (także "Więcej"); null - nieznana. */
fun bottomDestination(route: String): BottomDestination? = when (route) {
    ElektronRoutes.TRANSIT -> transitDestination
    MoreMenu.ROUTE -> moreDestination
    else -> bottomDestinations.firstOrNull { it.route == route }
}

fun bottomDestinationsForTransit(enabled: Boolean, order: List<String> = emptyList()): List<BottomDestination> {
    val all = bottomDestinations.toMutableList().apply { if (enabled) add(3, transitDestination) }.associateBy { it.route }
    return NavigationOrder.normalize(order).mapNotNull(all::get)
}

fun visibleBottomDestinations(hidden: Set<String>, showTransit: Boolean, order: List<String> = emptyList()): List<BottomDestination> =
    bottomDestinationsForTransit(showTransit, order).filter { it.route == ElektronRoutes.SETTINGS || it.route !in hidden }

/** Skąd przeciągana jest ikona: z dolnego pasku albo z panelu "Więcej". */
enum class DockSource { BAR, PANEL }

/**
 * Wspólny stan dolnego pasku i panelu "Więcej" - ikona przechodzi między nimi w jednym geście, jak
 * między ekranem głównym a folderem na Androidzie. Pozycje w pikselach okna (pasek, panel i warstwa
 * z przeciąganą ikoną leżą w różnych miejscach drzewa). Podgląd ([bar], [panel]) zmienia się w trakcie
 * przeciągania; zapis dopiero po puszczeniu ([finish]).
 */
@Stable
class DockState(initialBar: List<String>, initialPanel: List<String>) {
    /** Zapisany układ (z ustawień). */
    var savedBar by mutableStateOf(initialBar); private set
    var savedPanel by mutableStateOf(initialPanel); private set
    var bar by mutableStateOf(initialBar)
    var panel by mutableStateOf(initialPanel)
    var open by mutableStateOf(false)
    var dragged by mutableStateOf<String?>(null); private set
    var source by mutableStateOf(DockSource.BAR); private set
    /** Palec w pikselach okna. */
    var finger by mutableStateOf(Offset.Zero); private set
    /** Palec nad środkiem przycisku "Więcej" - puszczenie wkłada ikonę do panelu. */
    var hoverMore by mutableStateOf(false); private set
    /** Palec nad paskiem (ikona trzyma się wtedy linii pasku, jak dawniej). */
    var overBar by mutableStateOf(true); private set
    var pending by mutableStateOf(false)
    var barRect by mutableStateOf(Rect.Zero)
    var panelGridRect by mutableStateOf(Rect.Zero)
    var panelRect by mutableStateOf(Rect.Zero)
    var columns = 4
    var rtl = false
    var slackPx = 0f

    val dragging: Boolean get() = dragged != null

    fun sync(newBar: List<String>, newPanel: List<String>) {
        savedBar = newBar; savedPanel = newPanel
        if (!dragging && !pending) { bar = newBar; panel = newPanel }
        if (MoreMenu.ROUTE !in newBar) open = false
    }

    /** Czy [route] może trafić do panelu: nie "Więcej" i na pasku zostaje choć jedna zakładka. */
    fun canFold(route: String): Boolean =
        MoreMenu.ROUTE in bar && route != MoreMenu.ROUTE && bar.count { it != MoreMenu.ROUTE && it != route } >= 1

    fun start(route: String, from: DockSource, at: Offset) {
        if (pending) return
        dragged = route; source = from; finger = at; hoverMore = false; overBar = from == DockSource.BAR
    }

    fun move(to: Offset) {
        val route = dragged ?: return
        finger = to
        val inPanel = open && canFold(route) && panelRect.inflate(slackPx).contains(to)
        val inBar = !inPanel && to.y >= barRect.top - slackPx
        overBar = inBar
        when {
            inPanel -> {
                hoverMore = false
                bar = bar - route
                val rest = panel - route
                panel = rest.toMutableList().apply { add(panelIndex(to, rest.size), route) }
            }
            inBar -> {
                // Panel zostaje otwarty do puszczenia (zamknięty w trakcie zabrałby gest) - zamyka się po upuszczeniu na pasek.
                if (route in panel) panel = panel - route
                val withRoute = if (route in bar) bar else bar.toMutableList().apply {
                    add(NavigationOrder.targetIndex(to.x - barRect.left, barRect.width, size + 1, rtl), route)
                }
                val moreIndex = withRoute.indexOf(MoreMenu.ROUTE)
                val slot = NavigationOrder.targetIndex(to.x - barRect.left, barRect.width, withRoute.size, rtl)
                val slotLeft = barRect.left + NavigationOrder.slotLeft(slot, barRect.width, withRoute.size, rtl)
                val slotWidth = barRect.width / withRoute.size
                if (slot == moreIndex && route != MoreMenu.ROUTE) {
                    // Nad "Więcej": środek (połowa szerokości) - do panelu; zamiana miejsc dopiero za środkiem, w dalszej
                    // ćwiartce przycisku (dawniej sam brzeg przesuwał "Więcej", zanim palec doszedł do środka).
                    val fromLeft = NavigationOrder.slotLeft(withRoute.indexOf(route), barRect.width, withRoute.size, rtl) < slotLeft - barRect.left
                    val within = (to.x - slotLeft) / slotWidth
                    hoverMore = canFold(route) && within in 0.25f..0.75f
                    val pastMore = if (fromLeft) within > 0.8f else within < 0.2f
                    bar = if (pastMore && !hoverMore) NavigationOrder.move(withRoute, route, slot) else withRoute
                } else {
                    hoverMore = false
                    bar = NavigationOrder.move(withRoute, route, slot)
                }
            }
            else -> hoverMore = false
        }
    }

    /** Komórka panelu pod palcem (wstawienie do listy [count] pozostałych ikon). */
    private fun panelIndex(at: Offset, count: Int): Int {
        if (panelGridRect.width <= 0f) return count
        return gridIndex(at.x - panelGridRect.left, at.y - panelGridRect.top, count + 1, panelGridRect.width).coerceIn(0, count)
    }
    var cellHeightPx = 0f

    /** Rzędy panelu są wyśrodkowane: niepełny rząd (np. 3 ikony) zaczyna się od takiego przesunięcia. */
    fun rowOffset(row: Int, count: Int, width: Float): Float {
        val inRow = (count - row * columns).coerceIn(0, columns)
        return (columns - inRow) * (width / columns) / 2
    }

    /** Lewa krawędź komórki [index] przy [count] ikonach w panelu szerokości [width]. */
    fun cellLeft(index: Int, count: Int, width: Float): Float {
        val row = index / columns
        val column = index % columns
        val inRow = (count - row * columns).coerceIn(1, columns)
        val visual = if (rtl) inRow - 1 - column else column
        return rowOffset(row, count, width) + visual * (width / columns)
    }

    /** Ikona (indeks) pod punktem panelu przy [count] ikonach - z wyśrodkowaniem rzędów. */
    fun gridIndex(x: Float, y: Float, count: Int, width: Float): Int {
        val cellWidth = width / columns
        val cellHeight = cellHeightPx.takeIf { it > 0f } ?: cellWidth
        val rows = ((count + columns - 1) / columns).coerceAtLeast(1)
        val row = (y / cellHeight).toInt().coerceIn(0, rows - 1)
        val inRow = (count - row * columns).coerceIn(1, columns)
        val visual = ((x - rowOffset(row, count, width)) / cellWidth).toInt().coerceIn(0, inRow - 1)
        val column = if (rtl) inRow - 1 - visual else visual
        return row * columns + column
    }

    /** Puszczenie: nowy układ do zapisu (null - bez zmian albo upuszczone poza paskiem i panelem). */
    fun finish(): Pair<List<String>, List<String>>? {
        val route = dragged ?: return null
        if (hoverMore) { bar = bar - route; panel = (panel - route) + route }
        val placed = route in bar || route in panel
        // Jak folder na Androidzie: ikona wyciągnięta z panelu na pasek zamyka panel.
        if (source == DockSource.PANEL && route in bar) open = false
        dragged = null; hoverMore = false; overBar = true
        if (!placed || (bar == savedBar && panel == savedPanel)) { bar = savedBar; panel = savedPanel; return null }
        return bar to panel
    }

    fun cancel() { dragged = null; hoverMore = false; overBar = true; bar = savedBar; panel = savedPanel }
}

/** Wysokość pasku: ikona, odstępy i jedna linia podpisu (rośnie z czcionką systemową). */
@Composable
private fun barHeight(): Dp = (68 + 16 * LocalDensity.current.fontScale).dp

/** Jedna wielkość podpisów dla [captions] w komórce [cellWidth]: najdłuższy mieści się w jednej linii. */
@Composable
private fun rememberLabelStyle(captions: List<String>, cellWidth: Dp): TextStyle {
    val density = LocalDensity.current
    val base = MaterialTheme.typography.labelSmall
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val widthPx = with(density) { (cellWidth - 8.dp).toPx() }
    return remember(captions, widthPx, base, density) {
        val widest = captions.maxOfOrNull { measurer.measure(it, base, maxLines = 1, softWrap = false).size.width } ?: 0
        // Najwyżej do 50%; 3% zapasu - pomiar "na styk" kończył się wielokropkiem.
        val scale = if (widest <= 0 || widest <= widthPx * 0.97f) 1f else (widthPx * 0.97f / widest).coerceAtLeast(0.5f)
        fun androidx.compose.ui.unit.TextUnit.scaled() = if (isSpecified) this * scale else this
        if (scale == 1f) base else base.copy(fontSize = base.fontSize.scaled(), letterSpacing = base.letterSpacing.scaled())
    }
}

private fun caption(route: String, label: String) = if (route == ElektronRoutes.DASHBOARD) "Start" else label

/** Ikona w kółku z podpisem - ta sama na pasku, w panelu i w przeciąganiu. */
@Composable
private fun DockIcon(dest: BottomDestination, selected: Boolean, lifted: Boolean, highlighted: Boolean, width: Dp,
    labelStyle: TextStyle, interaction: MutableInteractionSource?, dropping: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val color = if (selected) colors.primary else colors.onSurfaceVariant
    // Nad "Więcej" przeciągana ikona maleje i chowa podpis, a "Więcej" rośnie pod nią - jak ikona nad folderem.
    val scale by animateFloatAsState(when { dropping -> 0.72f; lifted -> 1.12f; highlighted -> 1.22f; else -> 1f }, label = "dockLift")
    val labelAlpha by animateFloatAsState(if (dropping) 0f else 1f, label = "dockLabel")
    val labelHeight = (16 * LocalDensity.current.fontScale).dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(minOf(48.dp, width)).graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(if (lifted) 4.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background(when {
                    highlighted -> colors.primaryContainer
                    selected || lifted -> colors.secondaryContainer
                    else -> colors.surfaceContainer
                })
                .then(if (interaction != null) Modifier.indication(interaction, LocalIndication.current) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (selected || highlighted) dest.selectedIcon else dest.icon, null,
                tint = if (highlighted) colors.onPrimaryContainer else color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(width).height(labelHeight).padding(horizontal = 4.dp), contentAlignment = Alignment.TopCenter) {
            Text(caption(dest.route, dest.label), color = color, style = labelStyle,
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha })
        }
    }
}

/** "1 zakładka", "3 zakładki", "5 zakładek". */
private fun tabs(count: Int): String = "$count " + when {
    count == 1 -> "zakładka"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "zakładki"
    else -> "zakładek"
}

/**
 * Dolny pasek. Przytrzymanie ikony i przeciągnięcie zmienia kolejność; z włączonym "Więcej" ikonę można też
 * upuścić na środek "Więcej" (albo w otwarty panel), a ikonę z panelu - na pasek. [onCommit] dostaje nowy
 * pasek i panel; wywołuje done(sukces).
 */
@Composable
fun ElektronBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    dock: DockState,
    onCommit: (List<String>, List<String>, (Boolean) -> Unit) -> Unit
) {
    val routes = dock.bar
    if (routes.isEmpty()) return
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    dock.rtl = rtl
    dock.slackPx = with(density) { 24.dp.toPx() }
    val latestCommit by rememberUpdatedState(onCommit)
    val commit: (List<String>, List<String>) -> Unit = { bar, panel ->
        dock.bar = bar; dock.panel = panel; dock.pending = true
        latestCommit(bar, panel) { success ->
            dock.pending = false
            if (!success) { dock.bar = dock.savedBar; dock.panel = dock.savedPanel }
        }
    }
    val latestCommitLocal by rememberUpdatedState(commit)
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Ten sam "Więcej" przytrzymany na środku przez chwilę otwiera panel, jak folder.
    LaunchedEffect(dock.hoverMore) { if (dock.hoverMore) { delay(450); if (dock.hoverMore && dock.dragging) dock.open = true } }
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(NavigationBarDefaults.windowInsets).height(barHeight())
                .onGloballyPositioned { origin = it.positionInWindow(); dock.barRect = it.boundsInWindow() }) {
                val widthPx = constraints.maxWidth.toFloat()
                val count = routes.size
                val slotWidth = maxWidth / count
                val labelStyle = rememberLabelStyle(routes.mapNotNull { r -> bottomDestination(r)?.let { caption(r, it.label) } }, slotWidth)
                val panelRoutes = dock.panel
                // Klucz bez listy ikon: zmienia się w trakcie przeciągania (ikona do panelu), a nowy klucz przerwałby gest.
                Box(Modifier.fillMaxSize().pointerInput(widthPx, rtl) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { point ->
                            if (!dock.pending && dock.bar.size > 1) {
                                val index = NavigationOrder.targetIndex(point.x, widthPx, dock.bar.size, rtl)
                                dock.bar.getOrNull(index)?.let { dock.start(it, DockSource.BAR, origin + point) }
                            }
                        },
                        onDrag = { change, amount ->
                            if (dock.dragging) { change.consume(); dock.move(dock.finger + amount) }
                        },
                        onDragEnd = { dock.finish()?.let { (bar, panel) -> latestCommitLocal(bar, panel) } },
                        onDragCancel = { dock.cancel() }
                    )
                }) {
                    routes.forEachIndexed { index, route ->
                        val dest = bottomDestination(route) ?: return@forEachIndexed
                        key(route) {
                            val isDragged = dock.dragged == route
                            val targetX = NavigationOrder.slotLeft(index, widthPx, count, rtl)
                            val animatedX by animateFloatAsState(targetX,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), label = "tabPosition")
                            val interaction = remember { MutableInteractionSource() }
                            val isMore = route == MoreMenu.ROUTE
                            val selected = if (isMore) dock.open || (currentRoute != null && currentRoute in panelRoutes) else currentRoute == route
                            Box(
                                Modifier.align(AbsoluteAlignment.TopLeft).width(slotWidth).fillMaxHeight()
                                    .graphicsLayer { translationX = animatedX; alpha = if (isDragged) 0f else 1f }
                                    .clickable(interactionSource = interaction, indication = null,
                                        enabled = !dock.dragging && !dock.pending, role = Role.Tab) {
                                        if (isMore) dock.open = !dock.open else { dock.open = false; onNavigate(route) }
                                    }
                                    .semantics(mergeDescendants = true) {
                                        this.selected = selected
                                        contentDescription = if (isMore) "Więcej, ${tabs(panelRoutes.size)}" else dest.label
                                        if (isMore) stateDescription = if (dock.open) "Rozwinięte" else "Zwinięte"
                                        else stateDescription = "Pozycja ${index + 1} z $count"
                                        customActions = buildList {
                                            if (!dock.pending && index > 0) add(CustomAccessibilityAction("Przesuń wcześniej") {
                                                latestCommitLocal(NavigationOrder.move(dock.bar, route, index - 1), dock.panel); true
                                            })
                                            if (!dock.pending && index < dock.bar.lastIndex) add(CustomAccessibilityAction("Przesuń później") {
                                                latestCommitLocal(NavigationOrder.move(dock.bar, route, index + 1), dock.panel); true
                                            })
                                            if (!dock.pending && dock.canFold(route)) add(CustomAccessibilityAction("Przenieś do Więcej") {
                                                latestCommitLocal(dock.bar - route, dock.panel + route); true
                                            })
                                        }
                                    }.padding(vertical = 8.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                DockIcon(dest, selected, lifted = false, highlighted = isMore && dock.hoverMore, width = slotWidth,
                                    labelStyle = labelStyle, interaction = interaction)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Panel "Więcej" nad dolnym paskiem: siatka ikon jak folder na Androidzie. Dotknięcie otwiera zakładkę,
 * przytrzymanie i przeciągnięcie zmienia kolejność albo przenosi ikonę na pasek. Rysowany w obszarze treści
 * (dolna krawędź nad paskiem), z przyciemnieniem, które zamyka panel po dotknięciu.
 */
@Composable
fun BoxScope.MorePanel(currentRoute: String?, dock: DockState, onNavigate: (String) -> Unit,
    onCommit: (List<String>, List<String>, (Boolean) -> Unit) -> Unit) {
    val visible = dock.open && MoreMenu.ROUTE in dock.bar
    val scrimAlpha by animateFloatAsState(if (visible) 0.32f else 0f, tween(280), label = "moreScrim")
    if (scrimAlpha > 0f) Box(Modifier.matchParentSize().graphicsLayer { alpha = scrimAlpha }
        .background(MaterialTheme.colorScheme.scrim)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClickLabel = "Zwiń Więcej") { if (!dock.dragging) dock.open = false })
    val density = LocalDensity.current
    val latestCommit by rememberUpdatedState(onCommit)
    val commit: (List<String>, List<String>) -> Unit = { bar, panel ->
        dock.bar = bar; dock.panel = panel; dock.pending = true
        latestCommit(bar, panel) { success ->
            dock.pending = false
            if (!success) { dock.bar = dock.savedBar; dock.panel = dock.savedPanel }
        }
    }
    val latestCommitLocal by rememberUpdatedState(commit)
    val cellHeight = barHeight()
    AnimatedVisibility(visible, modifier = Modifier.align(Alignment.BottomCenter),
        // Płynne rozwinięcie od pasku: wysokość, lekkie powiększenie od dołu i przenikanie w jednym tempie.
        enter = expandVertically(tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing), expandFrom = Alignment.Bottom) +
            androidx.compose.animation.scaleIn(tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing), initialScale = 0.92f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)) + fadeIn(tween(260)),
        exit = shrinkVertically(tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing), shrinkTowards = Alignment.Bottom) +
            androidx.compose.animation.scaleOut(tween(260), targetScale = 0.94f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)) +
            fadeOut(tween(200))) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .onGloballyPositioned { dock.panelRect = it.boundsInWindow() },
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp, shadowElevation = 6.dp) {
            // Bez nagłówka "Więcej" - panel wyjaśnia sam przycisk pod nim.
            Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                val items = dock.panel
                val rows = ((items.size + dock.columns - 1) / dock.columns).coerceAtLeast(1)
                BoxWithConstraints(Modifier.fillMaxWidth().height(cellHeight * rows)) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val cellWidth = maxWidth / dock.columns
                    val cellHeightPx = with(density) { cellHeight.toPx() }
                    dock.cellHeightPx = cellHeightPx
                    var origin by remember { mutableStateOf(Offset.Zero) }
                    val labelStyle = rememberLabelStyle(items.mapNotNull { r -> bottomDestination(r)?.let { caption(r, it.label) } }, cellWidth)
                    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow(); dock.panelGridRect = it.boundsInWindow() }
                        .pointerInput(widthPx, cellHeightPx, dock.rtl) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { point ->
                                    if (!dock.pending && dock.panel.isNotEmpty()) {
                                        val index = dock.gridIndex(point.x, point.y, dock.panel.size, widthPx)
                                        dock.panel.getOrNull(index)?.let { dock.start(it, DockSource.PANEL, origin + point) }
                                    }
                                },
                                onDrag = { change, amount -> if (dock.dragging) { change.consume(); dock.move(dock.finger + amount) } },
                                onDragEnd = { dock.finish()?.let { (bar, panel) -> latestCommitLocal(bar, panel) } },
                                onDragCancel = { dock.cancel() }
                            )
                        }) {
                        if (items.isEmpty()) Text("Przytrzymaj ikonę na pasku i upuść ją na „Więcej”, aby ją tu przenieść.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
                        items.forEachIndexed { index, route ->
                            val dest = bottomDestination(route) ?: return@forEachIndexed
                            key(route) {
                                // Rzędy wyśrodkowane (3 ikony na środku, nie przy lewej krawędzi).
                                val x by animateFloatAsState(dock.cellLeft(index, items.size, widthPx),
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), label = "panelX")
                                val y by animateFloatAsState((index / dock.columns) * cellHeightPx,
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), label = "panelY")
                                val interaction = remember { MutableInteractionSource() }
                                val selected = currentRoute == route
                                Box(Modifier.align(AbsoluteAlignment.TopLeft).width(cellWidth).height(cellHeight)
                                    .graphicsLayer { translationX = x; translationY = y; alpha = if (dock.dragged == route) 0f else 1f }
                                    .clickable(interactionSource = interaction, indication = null, enabled = !dock.dragging && !dock.pending,
                                        role = Role.Tab) { dock.open = false; onNavigate(route) }
                                    .semantics(mergeDescendants = true) {
                                        this.selected = selected
                                        contentDescription = dest.label
                                        stateDescription = "W Więcej, pozycja ${index + 1} z ${items.size}"
                                        customActions = buildList {
                                            if (!dock.pending && index > 0) add(CustomAccessibilityAction("Przesuń wcześniej") {
                                                latestCommitLocal(dock.bar, NavigationOrder.move(dock.panel, route, index - 1)); true
                                            })
                                            if (!dock.pending && index < dock.panel.lastIndex) add(CustomAccessibilityAction("Przesuń później") {
                                                latestCommitLocal(dock.bar, NavigationOrder.move(dock.panel, route, index + 1)); true
                                            })
                                            if (!dock.pending) add(CustomAccessibilityAction("Przenieś na pasek") {
                                                val more = dock.bar.indexOf(MoreMenu.ROUTE).coerceAtLeast(0)
                                                latestCommitLocal(dock.bar.toMutableList().apply { add(more, route) }, dock.panel - route); true
                                            })
                                        }
                                    }.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.TopCenter) {
                                    DockIcon(dest, selected, lifted = false, highlighted = false, width = cellWidth,
                                        labelStyle = labelStyle, interaction = interaction)
                                }
                            }
                        }
                    }
                }
                Text(if (items.isEmpty()) "" else "Przytrzymaj ikonę, aby zmienić kolejność albo przenieść ją na pasek.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 8.dp, end = 8.dp))
            }
        }
    }
}

/**
 * Przeciągana ikona ponad paskiem i panelem (rysowana raz, w jednej warstwie, więc płynnie przechodzi
 * między nimi). Nad paskiem trzyma się jego linii, jak dawniej; poza nim podąża za palcem.
 */
@Composable
fun BoxScope.DockDragLayer(dock: DockState, currentRoute: String?) {
    val route = dock.dragged ?: return
    val dest = bottomDestination(route) ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val slotWidth = with(density) {
        if (dock.barRect.width > 0f && dock.bar.isNotEmpty()) (dock.barRect.width / dock.bar.size.coerceAtLeast(1)).toDp() else 72.dp
    }
    val widthPx = with(density) { slotWidth.toPx() }
    val labelStyle = rememberLabelStyle(listOf(caption(route, dest.label)), slotWidth)
    val lockedY = dock.barRect.top + with(density) { 8.dp.toPx() }
    val fingerTop = dock.finger.y - with(density) { 32.dp.toPx() }
    val y by animateFloatAsState(if (dock.overBar) lockedY else fingerTop,
        spring(stiffness = Spring.StiffnessHigh), label = "dragY")
    Box(Modifier.matchParentSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        Box(Modifier.zIndex(10f).offset { IntOffset((dock.finger.x - widthPx / 2 - origin.x).roundToInt(), (y - origin.y).roundToInt()) }
            .width(slotWidth)) {
            DockIcon(dest, selected = currentRoute == route, lifted = true, highlighted = false, width = slotWidth,
                labelStyle = labelStyle, interaction = null, dropping = dock.hoverMore)
        }
    }
}
