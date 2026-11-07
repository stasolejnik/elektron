package pl.zse.bydgoszcz.elektron.domain.model

/** Zapis sali w interfejsie. */
object Rooms {
    /**
     * "s. 111" bez przerwy wiersza między "s." a numerem (także przy dużej czcionce). Sama twarda spacja wystarcza
     * w aplikacji, ale panel powiadomień Androida 16 łamał przy niej wiersz - dlatego dodatkowo łączniki słów (U+2060).
     */
    fun label(room: String): String = "s.\u2060\u00A0\u2060$room"

    /** Tekst do udostępnienia (inne aplikacje, schowek): bez niewidocznych łączników i twardych spacji. */
    fun plain(text: String): String = text.replace("\u2060", "").replace('\u00A0', ' ')
}
