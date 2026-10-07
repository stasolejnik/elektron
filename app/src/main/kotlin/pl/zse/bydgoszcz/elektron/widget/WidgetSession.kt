package pl.zse.bydgoszcz.elektron.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import android.util.Log
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable

/**
 * Wersja danych widżetów. Glance przez pewien czas po odświeżeniu trzyma otwartą sesję
 * widżetu i wtedy updateAll() tylko go przerysowuje - bez ponownego provideGlance, czyli
 * ze starymi danymi (zmiana krycia czy nazw przedmiotów nie docierała do widżetu).
 * Każde żądanie odświeżenia zwiększa wersję, a otwarta sesja wczytuje wtedy dane od nowa.
 */
object WidgetDataVersion {
    private val _value = MutableStateFlow(0L)
    val value: StateFlow<Long> = _value

    fun bump() = _value.update { it + 1 }
}

/** Dane widżetu wczytane przed provideContent, wczytywane ponownie przy każdej nowej wersji. */
class WidgetSnapshot<T>(val data: T, val version: Long)

suspend fun <T> loadSnapshot(load: suspend () -> T): WidgetSnapshot<T> {
    val version = WidgetDataVersion.value.value   // przed wczytaniem - zmiana w trakcie nie przepadnie
    return WidgetSnapshot(load(), version)
}

@Composable
fun <T> rememberLiveWidgetData(initial: WidgetSnapshot<T>, load: suspend () -> T, reconcile: (T, T) -> T = { _, fresh -> fresh }): T {
    val version by WidgetDataVersion.value.collectAsState()
    var data by remember { mutableStateOf(initial.data) }
    var loadedVersion by remember { mutableLongStateOf(initial.version) }
    LaunchedEffect(version) {
        if (version != loadedVersion) {
            val v = version
            // Błąd odczytu (np. baza chwilowo niedostępna) - zostają ostatnie dane. Dawniej
            // wyjątek wywracał sesję widżetu i Glance pokazywał "Nie można wczytać widżetu".
            runCatchingCancellable { load() }
                .onSuccess { data = reconcile(data, it) }
                .onFailure { Log.w("WidgetSession", "Nie udało się odświeżyć danych widżetu", it) }
            loadedVersion = v
        }
    }
    return data
}
