package pl.zse.bydgoszcz.elektron.data.remote.dto

/** Komórka td.l — jedna lekcja z listą grup. */
data class LessonCellDto(
    val number: Int,
    val timeFrom: String,
    val timeTo: String,
    val dayIndex: Int,
    val groups: List<LessonGroupDto>,
    val note: String?
)

/** Podgrupa w komórce (przedmiot + nauczyciel + sala, kolejność zależna od widoku). */
data class LessonGroupDto(
    val subject: String?,
    val teacherCode: String?,
    val teacherUrl: String?,
    val room: String?,
    val roomUrl: String?,
    val classRef: String?,
    val groupLabel: String?
)
