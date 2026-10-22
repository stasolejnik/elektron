package pl.zse.bydgoszcz.elektron.domain.util

/**
 * Wpis do logu tylko przy zmianie stanu. Przeliczenia co minutę (powiadomienie na żywo, widżety)
 * zapełniały raport "Zgłoś problem" (ostatnie 150 linii) powtórzeniami tego samego stanu i wypychały
 * z niego błędy synchronizacji czy ukrycia powiadomienia.
 */
class LogOnChange {
    @Volatile private var last: Any? = null

    /** true - [key] różni się od poprzedniego (warto zapisać); zapamiętuje go. */
    @Synchronized fun changed(key: Any?): Boolean {
        if (key == last) return false
        last = key
        return true
    }
}
