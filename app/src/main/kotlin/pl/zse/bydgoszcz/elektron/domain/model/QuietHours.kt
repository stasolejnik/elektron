package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalTime

/**
 * Ciche godziny: powiadomienia przychodzą bez dźwięku i wibracji (nie giną - zastępstwo
 * ogłoszone wieczorem ma rano czekać na ekranie). Zakres może przechodzić przez północ.
 */
data class QuietHours(
    val enabled: Boolean = false,
    val from: LocalTime = LocalTime.of(22, 0),
    val to: LocalTime = LocalTime.of(7, 0)
) {
    fun isQuiet(time: LocalTime): Boolean = when {
        !enabled || from == to -> false
        from < to -> time >= from && time < to
        else -> time >= from || time < to      // przez północ, np. 22:00-7:00
    }
}
