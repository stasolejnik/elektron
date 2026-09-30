package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Wskaźnik ładowania pokazywany dopiero, gdy ładowanie trwa dłużej niż [delayMillis].
 * Dane z bazy przychodzą zwykle po kilkudziesięciu ms - natychmiastowy spinner dawał
 * tylko mignięcie przed treścią.
 */
@Composable
fun DelayedLoading(delayMillis: Long = LOADING_DELAY_MS, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        show = true
    }
    AnimatedVisibility(visible = show, enter = fadeIn(tween(200)), exit = ExitTransition.None) { content() }
}

/**
 * Treść ekranu po pierwszym wczytaniu danych. Do tego czasu - samo tło (zamiast chwilowego
 * "Brak zastępstw" czy przełączników w domyślnym położeniu), potem krótkie pojawienie się.
 * Gdy dane są już wczytane (powrót do zakładki) - treść od razu, bez animacji.
 */
@Composable
fun FirstLoad(loaded: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = loaded, enter = fadeIn(tween(160)), exit = ExitTransition.None) { content() }
}

/**
 * Jak [FirstLoad], ale jako modyfikator (bez przebudowy układu): do pierwszego wczytania
 * treść jest niewidoczna, potem płynnie się pojawia. Gdy [ready] = true od początku
 * (powrót do zakładki) - widoczna od razu.
 */
fun Modifier.revealWhen(ready: Boolean): Modifier = composed {
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(160), label = "reveal")
    graphicsLayer { this.alpha = alpha }
}

const val LOADING_DELAY_MS = 400L
