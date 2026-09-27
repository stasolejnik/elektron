package pl.zse.bydgoszcz.elektron.domain.model

/** Podgrupa w komórce planu. */
data class LessonGroup(
    val subject: String?,
    val teacherCode: String?,
    val teacherUrl: String?,
    val teacherFullName: String?,
    val room: String?,
    val roomUrl: String?,
    val groupLabel: String?,
    val classRef: String?
)
