package pl.zse.bydgoszcz.elektron.domain.model

/** Zapis sali w interfejsie. */
object Rooms {
    /** "s. 111" z twardą spacją - numer sali nie przechodzi sam do następnego wiersza (także przy dużej czcionce). */
    fun label(room: String): String = "s. $room"
}
