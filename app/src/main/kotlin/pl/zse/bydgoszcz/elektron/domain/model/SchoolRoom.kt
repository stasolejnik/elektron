package pl.zse.bydgoszcz.elektron.domain.model

/** Sala (nazwa unikalna, bo "Hala2" występuje jako jedna). */
data class SchoolRoom(
    val id: String,
    val name: String,
    val url: String
)
