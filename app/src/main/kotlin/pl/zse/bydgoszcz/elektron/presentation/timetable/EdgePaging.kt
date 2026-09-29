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
import kotlin.math.sign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity

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
 * Zasady chroniące przed przypadkowym przełączeniem - patrz EdgePagingConnection.
 */
@Composable
fun rememberEdgePagingConnection(pagerState: PagerState): NestedScrollConnection {
    val fling = PagerDefaults.flingBehavior(
        state = pagerState,
        // 40% strony przy spokojnym ruchu. Dawniej 15% - przeglądanie lekcji dnia co chwilę
        // przypadkiem przerzucało na następny dzień.
        snapPositionalThreshold = 0.4f,
        snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    )
    val deadZonePx = with(LocalDensity.current) { DEAD_ZONE.toPx() }
    return remember(pagerState, fling, deadZonePx) { EdgePagingConnection(pagerState, fling, deadZonePx) }
}

/** Tyle trzeba przeciągnąć za koniec listy, zanim ruszy strona (przypadkowe dociągnięcia). */
private val DEAD_ZONE = 56.dp

/** Poniżej tego przesunięcia strony szybki rzut nie zmienia dnia - strona wraca. */
private const val MIN_FRACTION_FOR_FLING = 0.15f

/**
 * Zasady (poprawka 0.6 - przypadkowe przełączanie dni):
 *  1. Gest, który przewinął listę lekcji, nie zmienia dnia - nawet gdy dojedzie do jej końca.
 *     Żeby zmienić dzień, trzeba zacząć NOWY gest przy końcu listy.
 *  2. Strona rusza dopiero po przeciągnięciu [DEAD_ZONE] za koniec listy.
 *  3. Przy małym przesunięciu strony rzut palcem nie przełącza (tylko pozycja - 40%).
 */
private class EdgePagingConnection(
    private val pager: PagerState,
    private val fling: FlingBehavior,
    private val deadZonePx: Float
) : NestedScrollConnection {

    /** Lista przewinęła się w bieżącym geście - ten gest nie zmienia dnia. */
    private var listMoved = false
    /** Nadmiar ruchu zebrany w martwej strefie (znak = kierunek). */
    private var slack = 0f

    private val pagerMoved: Boolean get() = abs(pager.currentPageOffsetFraction) > 0.001f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Strona już przesunięta: ruch (także w drugą stronę) najpierw wraca/przesuwa stronę,
        // zamiast przewijać listę pod spodem.
        if (source != NestedScrollSource.UserInput || available.y == 0f || !pagerMoved) return Offset.Zero
        val used = pager.dispatchRawDelta(-available.y)
        return Offset(0f, -used)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (consumed.y != 0f && !pagerMoved) {
            listMoved = true
            slack = 0f
        }
        if (available.y == 0f || listMoved) return Offset.Zero

        // Martwa strefa: zbieramy nadmiar, zmiana kierunku zaczyna liczenie od nowa.
        if (!pagerMoved) {
            val dir: Float = available.y
            if (slack != 0f && slack.sign != dir.sign) slack = 0f
            slack += available.y
            // W martwej strefie nic nie zużywamy - lista pokazuje zwykłe "rozciągnięcie" na końcu.
            if (abs(slack) < deadZonePx) return Offset.Zero
            val beyond: Float = slack - slack.sign * deadZonePx
            slack = 0f
            val used = pager.dispatchRawDelta(-beyond)
            return Offset(0f, -used)
        }
        val used = pager.dispatchRawDelta(-available.y)
        return Offset(0f, -used)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // Koniec gestu - następny zaczyna od zera.
        listMoved = false
        slack = 0f
        if (!pagerMoved) return Velocity.Zero
        // Domknięcie strony natywnym rzutem pagera, z prędkością palca (+ = do przodu);
        // przy małym przesunięciu bez prędkości - decyduje sama pozycja.
        val velocity = if (abs(pager.currentPageOffsetFraction) < MIN_FRACTION_FOR_FLING) 0f else -available.y
        pager.scroll { with(fling) { performFling(velocity) } }
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        listMoved = false
        slack = 0f
        return Velocity.Zero
    }
}
