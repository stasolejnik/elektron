package pl.zse.bydgoszcz.elektron.data.remote.sources

import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto

/**
 * Źródło planu lekcji (Optivum VULCAN, UTF-8).
 * Implementacja: ZseTimetableSource.
 */
interface TimetableSource {
    suspend fun fetchSidebar(): List<ClassListItemDto>
    suspend fun fetchTimetable(classId: String): TimetableDto
}
