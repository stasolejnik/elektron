package pl.zse.bydgoszcz.elektron.domain.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Zegar "szkolny" aplikacji: plan, zastępstwa, strona główna, Odjazdy i widżety pytają o
 * aktualną datę i godzinę tutaj. Zwykle to czas telefonu; w trybie dewelopera można ustawić
 * symulowany czas (przesunięcie względem zegara telefonu - symulowany czas dalej płynie).
 *
 * Bez przesunięcia zostają: synchronizacja i jej odstępy, pamięć podręczna, limity zapytań,
 * przypomnienia i powiadomienia (alarmy systemu działają w prawdziwym czasie).
 */
object AppClock {
    private val offset = MutableStateFlow(0L)

    /** Przesunięcie w ms (0 - czas telefonu). Zmiana przelicza ekrany i widżety. */
    val offsetMs: StateFlow<Long> get() = offset

    val simulated: Boolean get() = offset.value != 0L

    /** Zapis przesunięcia (ustawiany przez aplikację: przetrwa ponowne uruchomienie procesu widżetu). */
    @Volatile var persist: (Long) -> Unit = {}

    fun millis(): Long = System.currentTimeMillis() + offset.value
    fun now(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis()), ZoneId.systemDefault())
    fun today(): LocalDate = now().toLocalDate()
    fun time(): LocalTime = now().toLocalTime()

    /** Symulowany czas zaczyna płynąć od [at]. */
    fun simulate(at: LocalDateTime) =
        setOffset(at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis())

    fun reset() = setOffset(0L)

    /** Odczyt zapisanego przesunięcia przy starcie (bez ponownego zapisu). */
    fun restore(offsetMs: Long) { offset.value = offsetMs }

    private fun setOffset(value: Long) {
        offset.value = value
        persist(value)
    }
}
