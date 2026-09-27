package pl.zse.bydgoszcz.elektron.data.remote.dto

/** Plan oddziału z plan.zse.bydgoszcz.pl/plany/o*.html. */
data class TimetableDto(
    val classId: String,
    val className: String,
    val generatedAt: String?,
    val validFrom: String?,
    val lessons: List<LessonCellDto>
)
