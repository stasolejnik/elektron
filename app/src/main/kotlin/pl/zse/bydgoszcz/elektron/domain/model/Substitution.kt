package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDate

data class Substitution(
    val id: String,
    val date: LocalDate,
    val lessonNumber: Int,
    val classShortName: String,
    val groupNumber: Int?,
    val roomOrInfo: String,
    val substituteTeacher: String?,
    val notes: String?,
    val originalTeacher: String,
    val originalSubject: String? = null
)
