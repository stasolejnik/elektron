package pl.zse.bydgoszcz.elektron.domain.model

/** Nauczyciel — code to skrót z planu (np. "Bo"), fullName z sidebaru. */
data class Teacher(
    val code: String,
    val fullName: String,
    val url: String
)
