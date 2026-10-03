package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.Job
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.ScrollableDefaults
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Kliknięcie w stylu iOS: element lekko się zmniejsza i przygasa przy dotyku
 * (sprężyna), bez materiałowego "ripple".
 */
fun Modifier.pressable(enabled: Boolean = true, onClickLabel: String? = null, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    val alpha by animateFloatAsState(if (pressed) 0.85f else 1f, label = "pressAlpha")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled,
            onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
}

/** Karta z domyślnym tłem powierzchni (biała / #1C1C1E), bez cienia. */
@Composable
fun ElektronCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    onClick: (() -> Unit)? = null,
    /** Opis akcji dla czytnika ekranu (TalkBack: "dwukrotnie dotknij, aby ..."). */
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClickLabel = onClickLabel, onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        content = content
    )
}

/** Nagłówek sekcji na ekranach list (ikona w kolorze akcentu + pogrubiony tytuł). */
@Composable
fun SectionTitle(title: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * Grupowana sekcja w stylu Ustawień iOS: mały nagłówek NAD kartą, zawartość w karcie,
 * opcjonalna stopka pod kartą.
 */
@Composable
fun GroupedSection(
    title: String?,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )
        }
        ElektronCard {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        }
    }
}

/**
 * Duży tytuł jak w iOS — zwija się do małego paska przy przewijaniu.
 * Ekran musi podpiąć scrollBehavior.nestedScrollConnection do Scaffolda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeTitleBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    LargeTopAppBar(
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

/**
 * Przewijanie listy zagnieżdżonej w innym przewijanym ekranie (np. lista klas w Ustawieniach)
 * TAK, żeby rusza się wyłącznie ta lista.
 *
 * Standardowe zagnieżdżone przewijanie w Compose przekazuje ruch rodzicom — i to zanim
 * dostanie go wewnętrzna lista (tak zwija się duży tytuł), a nadmiar po dojechaniu do
 * końca listy przewija cały ekran. Tutaj gest obsługujemy sami: przewijamy listę
 * bezpośrednio (dispatchRawDelta, z pominięciem zagnieżdżonego przewijania) i zużywamy
 * zdarzenia dotyku, więc ekran pod spodem ich nie widzi. Rzut palcem: domyślny fling.
 *
 * Lista musi mieć userScrollEnabled = false (żeby sama nie rozsyłała ruchu do rodziców).
 * Tapnięcia działają normalnie — gest przejmujemy dopiero po przekroczeniu progu ruchu.
 */
fun Modifier.isolatedVerticalScroll(state: LazyListState): Modifier = composed {
    val scope = rememberCoroutineScope()
    val fling = ScrollableDefaults.flingBehavior()
    val flingJob = remember { arrayOfNulls<Job>(1) }
    this.pointerInput(state) {
        val tracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = {
                flingJob[0]?.cancel()          // dotknięcie zatrzymuje trwający rzut
                tracker.resetTracking()
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                state.dispatchRawDelta(-dragAmount)
            },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().y
                flingJob[0] = scope.launch {
                    state.scroll { with(fling) { performFling(-velocity) } }
                }
            },
            onDragCancel = { tracker.resetTracking() }
        )
    }
}
