package pl.zse.bydgoszcz.elektron.data.remote.dto

data class SubstitutionDto(
    val dateRaw: String,
    val originalTeacher: String,
    val lessonNumber: Int,
    val classShortName: String,
    val groupNumber: Int?,
    val roomOrInfo: String,
    val substituteTeacher: String?,
    val notes: String?
)
