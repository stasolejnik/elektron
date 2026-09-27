package pl.zse.bydgoszcz.elektron.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.SchoolClass
import pl.zse.bydgoszcz.elektron.domain.model.SchoolRoom
import pl.zse.bydgoszcz.elektron.domain.model.Teacher
import java.time.LocalDate

interface TimetableRepository {
    suspend fun syncSidebar(): Result<Unit>
    suspend fun syncTimetable(classId: String, anchorDate: LocalDate): Result<Unit>
    fun observeClasses(): Flow<List<SchoolClass>>
    fun observeTeachers(): Flow<List<Teacher>>
    fun observeRooms(): Flow<List<SchoolRoom>>
    fun observeLessons(classId: String, from: LocalDate, to: LocalDate): Flow<List<Lesson>>
    fun observeAllLessons(from: LocalDate, to: LocalDate): Flow<List<Lesson>>
    suspend fun getLessonsOnce(classId: String, from: LocalDate, to: LocalDate): List<Lesson>
    suspend fun enrichWithTeacherNames(lessons: List<Lesson>): List<Lesson>
    /** Lekkie sprawdzenie (COUNT), czy w bazie są lekcje klasy w zakresie dat. */
    suspend fun hasLessons(classId: String, from: LocalDate, to: LocalDate): Boolean
}
