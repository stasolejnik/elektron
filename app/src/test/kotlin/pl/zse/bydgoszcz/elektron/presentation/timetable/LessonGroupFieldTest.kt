package pl.zse.bydgoszcz.elektron.presentation.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.zse.bydgoszcz.elektron.domain.model.JointGroups

class LessonGroupFieldTest {

    @Test
    fun jointDescriptionGoesToGroupNotTeacher() {
        assertEquals("Grupa 2, łączona z 1H", lessonGroupField("Grupa 2", JointGroups.describe("#1BH", "1B")))
        assertEquals("Grupa 2, łączona", lessonGroupField("Grupa 2", JointGroups.describe("#RHJ", "1B")))
        assertEquals("Łączona z 1H", lessonGroupField(null, "łączona z 1H"))
        assertEquals("Grupa łączona", lessonGroupField(null, "grupa łączona"))
        assertEquals("Grupa 1", lessonGroupField("Grupa 1", null))
        assertNull(lessonGroupField(null, null))
    }
}
