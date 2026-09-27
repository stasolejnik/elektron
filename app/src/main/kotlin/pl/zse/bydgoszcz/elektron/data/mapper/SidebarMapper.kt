package pl.zse.bydgoszcz.elektron.data.mapper

import pl.zse.bydgoszcz.elektron.data.local.RoomEntity
import pl.zse.bydgoszcz.elektron.data.local.SchoolClassEntity
import pl.zse.bydgoszcz.elektron.data.local.TeacherEntity
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto
import pl.zse.bydgoszcz.elektron.domain.model.SchoolClass
import pl.zse.bydgoszcz.elektron.domain.model.SchoolRoom
import pl.zse.bydgoszcz.elektron.domain.model.Teacher

object SidebarMapper {
    private val TEACHER_PAREN = Regex("^(.+?)\\s*\\(([^)]+)\\)\\s*$")

    fun toClassEntity(dto: ClassListItemDto) = SchoolClassEntity(
        id = dto.id,
        fullName = dto.displayName,
        shortName = dto.shortName,
        url = dto.url
    )

    fun toTeacherEntity(dto: ClassListItemDto): TeacherEntity {
        val m = TEACHER_PAREN.find(dto.displayName)
        val full = m?.groupValues?.get(1)?.trim() ?: dto.displayName
        val code = m?.groupValues?.get(2)?.trim() ?: dto.shortName
        return TeacherEntity(code = code, fullName = full, url = dto.url)
    }

    fun toRoomEntity(dto: ClassListItemDto) = RoomEntity(
        id = dto.id,
        name = dto.displayName,
        url = dto.url
    )

    fun classToDomain(e: SchoolClassEntity) = SchoolClass(e.id, e.fullName, e.shortName, e.url)
    fun teacherToDomain(e: TeacherEntity) = Teacher(e.code, e.fullName, e.url)
    fun roomToDomain(e: RoomEntity) = SchoolRoom(e.id, e.name, e.url)
}
