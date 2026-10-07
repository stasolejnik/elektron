package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import pl.zse.bydgoszcz.elektron.domain.model.NavigationOrder


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.outlined.DirectionsTransit
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import pl.zse.bydgoszcz.elektron.presentation.navigation.ElektronRoutes

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

fun bottomDestinationsForTransit(enabled: Boolean, order: List<String> = emptyList()): List<BottomDestination> {
    val all = bottomDestinations.toMutableList().apply {
        if (enabled) add(3, BottomDestination(ElektronRoutes.TRANSIT, "Odjazdy", Icons.Outlined.DirectionsTransit, Icons.Filled.DirectionsTransit))
    }.associateBy { it.route }
    return NavigationOrder.normalize(order).mapNotNull(all::get)
}

fun visibleBottomDestinations(hidden: Set<String>, showTransit: Boolean, order: List<String> = emptyList()): List<BottomDestination> =
    bottomDestinationsForTransit(showTransit, order).filter { it.route == ElektronRoutes.SETTINGS || it.route !in hidden }

@Composable
fun ElektronBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    destinations: List<BottomDestination> = bottomDestinations,
    onReorder: (List<String>, (Boolean) -> Unit) -> Unit = { _, done -> done(true) }
) {
    if (destinations.isEmpty()) return
    val routes = destinations.map { it.route }
    val latestRoutes by rememberUpdatedState(routes)
    val latestReorder by rememberUpdatedState(onReorder)
    var preview by remember { mutableStateOf(routes) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    var pending by remember { mutableStateOf(false) }
    var dragStartOrder by remember { mutableStateOf(routes) }
    LaunchedEffect(routes) { if (dragged == null && !pending) preview = routes }
    val commit: (List<String>) -> Unit = { order ->
        preview = order; pending = true
        latestReorder(order) { success ->
            pending = false
            if (!success) preview = latestRoutes
        }
    }
    val latestCommit by rememberUpdatedState(commit)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val labelHeight = (32 * density.fontScale).dp
    val barHeight = (68 + 32 * density.fontScale).dp
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(NavigationBarDefaults.windowInsets).height(barHeight)) {
                val widthPx = constraints.maxWidth.toFloat()
                val slotWidth = maxWidth / destinations.size
                val halfSlot = widthPx / destinations.size / 2
                val byRoute = destinations.associateBy { it.route }
                val displayOrder = preview.takeIf { it.toSet() == routes.toSet() } ?: routes
                Box(Modifier.fillMaxSize().pointerInput(destinations.map { it.route }.toSet(), widthPx, rtl) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { point ->
                            if (!pending && preview.size > 1) {
                                val index = NavigationOrder.targetIndex(point.x, widthPx, preview.size, rtl)
                                dragged = preview.getOrNull(index)
                                dragStartOrder = preview
                                dragCenter = NavigationOrder.slotLeft(index, widthPx, preview.size, rtl) + halfSlot
                            }
                        },
                        onDrag = { change, amount ->
                            dragged?.let { route ->
                                change.consume()
                                dragCenter = (dragCenter + amount.x).coerceIn(halfSlot, widthPx - halfSlot)
                                val target = NavigationOrder.targetIndex(dragCenter, widthPx, preview.size, rtl)
                                preview = NavigationOrder.move(preview, route, target)
                            }
                        },
                        onDragEnd = {
                            val changed = dragged != null && preview != dragStartOrder
                            dragged = null
                            if (changed) latestCommit(preview)
                        },
                        onDragCancel = { dragged = null; preview = latestRoutes }
                    )
                }) {
                    displayOrder.forEachIndexed { index, route ->
                        val dest = byRoute[route] ?: return@forEachIndexed
                        key(route) {
                            val isDragged = dragged == route
                            val targetX = if (isDragged) dragCenter - halfSlot else NavigationOrder.slotLeft(index, widthPx, displayOrder.size, rtl)
                            val animatedX by animateFloatAsState(targetX,
                                animationSpec = if (isDragged) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), label = "tabPosition")
                            val scale by animateFloatAsState(if (isDragged) 1.12f else 1f, label = "tabLift")
                            val interaction = remember { MutableInteractionSource() }
                            val selected = currentRoute == route
                            val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            val caption = if (route == ElektronRoutes.DASHBOARD) "Start" else dest.label
                            Column(
                                Modifier.align(AbsoluteAlignment.TopLeft).width(slotWidth).fillMaxHeight().zIndex(if (isDragged) 1f else 0f)
                                    .graphicsLayer {
                                        translationX = if (isDragged) targetX else animatedX
                                    }
                                    .clickable(interactionSource = interaction, indication = null,
                                        enabled = dragged == null && !pending, role = Role.Tab) { onNavigate(route) }
                                    .semantics(mergeDescendants = true) {
                                        this.selected = selected
                                        contentDescription = dest.label
                                        stateDescription = "Pozycja ${index + 1} z ${displayOrder.size}"
                                        customActions = buildList {
                                            if (!pending && index > 0) add(CustomAccessibilityAction("Przesuń wcześniej") {
                                                latestCommit(NavigationOrder.move(preview, route, index - 1)); true
                                            })
                                            if (!pending && index < preview.lastIndex) add(CustomAccessibilityAction("Przesuń później") {
                                                latestCommit(NavigationOrder.move(preview, route, index + 1)); true
                                            })
                                        }
                                    }.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                            ) {
                                // Keep the slot stationary in size: only the circular icon lifts.
                                // The shared interaction source confines pressed feedback to the circle.
                                Box(
                                    Modifier.size(minOf(48.dp, slotWidth)).graphicsLayer { scaleX = scale; scaleY = scale }
                                        .shadow(if (isDragged) 4.dp else 0.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(if (selected || isDragged) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainer)
                                        .indication(interaction, LocalIndication.current),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(if (selected) dest.selectedIcon else dest.icon, null, tint = color, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().height(labelHeight).padding(horizontal = 4.dp), contentAlignment = Alignment.TopCenter) {
                                    Text(caption, color = color, style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
