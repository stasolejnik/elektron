package pl.zse.bydgoszcz.elektron.data.remote.dto

/** Pozycja z sidebar lista.html (oddział / nauczyciel / sala). */
data class ClassListItemDto(
    val id: String,
    val kind: Kind,
    val displayName: String,
    val shortName: String,
    val url: String
) {
    enum class Kind { CLASS, TEACHER, ROOM }
}
