package pl.zse.bydgoszcz.elektron.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wiersze widżetu Plan dnia bez Glance (Android 17+) zapisywane dla usługi listy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayPlanRowStoreTest {
    private fun row(number: Int, note: String? = null, link: Boolean = true) = DayPlanRow(
        id = 1000L + number, number = "$number", title = "matematyka", meta = "08:00–08:45 · s. 105",
        note = note, userNote = if (number == 2) "Notatka: kartkówka" else null, userNoteLines = 2,
        numberColor = DayNight(0xFF0000FF.toInt(), 0xFF00FFFF.toInt()), titleColor = DayNight(-1, -2), metaColor = DayNight(-3, -4),
        noteColor = DayNight(-5, -6), userNoteColor = DayNight(-7, -8), background = DayNight(0, 0x33000000),
        description = "Jutro, lekcja $number, matematyka", lessonUri = if (link) "elektron://lesson/20780/$number" else null,
        lessonLink = if (link) "lesson/20780/$number" else null)

    @Test fun rowsSurviveSavingWithEmptyFields() {
        val rows = listOf(row(1, note = "za ostatnią lekcję"), row(2), row(3, link = false))
        assertEquals(rows, DayPlanRowStore.decode(DayPlanRowStore.encode(rows)))
        assertEquals(emptyList<DayPlanRow>(), DayPlanRowStore.decode(DayPlanRowStore.encode(emptyList())))
    }
}
