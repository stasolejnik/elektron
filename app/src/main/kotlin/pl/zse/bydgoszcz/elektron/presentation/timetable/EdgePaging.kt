package pl.zse.bydgoszcz.elektron.presentation.timetable

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * Pionowe przechodzenie między dniami/tygodniami w planie: przesunięcie w górę = następny,
 * w dół = poprzedni. Lista lekcji przewija się normalnie; ruch, którego lista nie zużyje
 * (np. dzień mieści się na ekranie albo lista jest na końcu), przesuwa pager.
 *
 * Pager w Compose sam nie przejmuje ruchu, którego wewnętrzna lista nie zużyła (jego
 * onPostScroll zwraca zero), dlatego to połączenie przekazuje mu nadmiar ruchu. Gdy strona
 * jest już przesunięta, dalszy ruch przejmuje sam pager (jego onPreScroll).
 *
 * Po puszczeniu palca stronę domyka NATYWNY mechanizm rzutu pagera z rzeczywistą prędkością
 * ruchu — ta sama fizyka co przy zwykłym przewijaniu stron. (Dawniej osobna animacja startowała
 * od zera prędkości, więc ruch był ucinany i "szarpał".)
 *
 * Pager musi mieć userScrollEnabled = false (steruje nim wyłącznie ten mechanizm).
 */
@Composable
fun rememberEdgePagingConnection(pagerState: PagerState): NestedScrollConnection {
    val fling = PagerDefaults.flingBehavior(
        state = pagerState,
        // Wystarczy ~15% strony przy spokojnym ruchu (domyślnie 50% — za trudno).
        snapPositionalThreshold = 0.15f,
        snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    )
    return remember(pagerState, fling) { EdgePagingConnection(pagerState, fling) }
}

private class EdgePagingConnection(
    private val pager: PagerState,
    private val fling: FlingBehavior
) : NestedScrollConnection {

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
        // Przestrzeń gestu -> przestrzeń przewijania: palec w górę (y < 0) = następna strona.
        val used = pager.dispatchRawDelta(-available.y)
        return Offset(0f, -used)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (abs(pager.currentPageOffsetFraction) < 0.001f) return Velocity.Zero
        // Domknięcie strony natywnym rzutem pagera, z prędkością palca (+ = do przodu).
        pager.scroll { with(fling) { performFling(-available.y) } }
        return available
    }
}
