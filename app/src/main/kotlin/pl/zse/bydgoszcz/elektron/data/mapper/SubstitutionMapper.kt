package pl.zse.bydgoszcz.elektron.data.mapper

import android.util.Log
import pl.zse.bydgoszcz.elektron.data.local.SubstitutionEntity
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object SubstitutionMapper {
    private const val TAG = "SubstitutionMapper"
    private val PL_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun toEntity(dto: SubstitutionDto): SubstitutionEntity? {
        val date = runCatching { LocalDate.parse(dto.dateRaw, PL_DATE) }.getOrNull() ?: run {
            Log.w(TAG, "Nie udało się sparsować daty: ${dto.dateRaw}")
            return null
        }
        val roomHash = (dto.roomOrInfo + "|" + (dto.substituteTeacher ?: "")).hashCode().toUInt().toString(16)
        val id = "${date.toEpochDay()}|${dto.originalTeacher}|${dto.lessonNumber}|${dto.classShortName}|${dto.groupNumber ?: 0}|$roomHash"
        return SubstitutionEntity(
            id = id,
            dateEpochDay = date.toEpochDay(),
            lessonNumber = dto.lessonNumber,
            classShortName = dto.classShortName,
            groupNumber = dto.groupNumber,
            roomOrInfo = dto.roomOrInfo,
            substituteTeacher = dto.substituteTeacher,
            notes = dto.notes,
            originalTeacher = dto.originalTeacher,
            originalSubject = null
        )
    }

    fun toDomain(e: SubstitutionEntity): Substitution = Substitution(
        id = e.id,
        date = LocalDate.ofEpochDay(e.dateEpochDay),
        lessonNumber = e.lessonNumber,
        classShortName = e.classShortName,
        groupNumber = e.groupNumber,
        roomOrInfo = e.roomOrInfo,
        substituteTeacher = e.substituteTeacher,
        notes = e.notes,
        originalTeacher = e.originalTeacher,
        originalSubject = e.originalSubject
    )
}
