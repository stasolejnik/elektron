package pl.zse.bydgoszcz.elektron.presentation.announcements

import java.text.Normalizer

/** Normalizacja tekstu do wyszukiwania: małe litery, bez polskich znaków i zbędnych spacji. */
object SearchText {
    private val MARKS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace('ł', 'l')          // "ł" nie rozkłada się w NFD
            .replace(SPACES, " ")
            .trim()
}
