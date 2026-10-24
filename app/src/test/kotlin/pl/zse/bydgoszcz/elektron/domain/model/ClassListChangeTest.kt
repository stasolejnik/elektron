package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.*
import org.junit.Test

class ClassListChangeTest {
    private fun c(id: String, short: String) = SchoolClass(id, "$short pełna", short, "")
    private val list = listOf(c("o1", "1A"), c("o2", "2B"), c("o3", "3D"))

    @Test fun staleWhenTheClassVanishedOrItsIdNowBelongsToAnotherClass() {
        assertFalse(ClassListChange.selectedIsStale("o3", "3D", list))
        assertFalse("wielkość liter", ClassListChange.selectedIsStale("o3", "3d", list))
        assertTrue("zniknęła", ClassListChange.selectedIsStale("o9", "4D", list))
        assertTrue("inna klasa pod tym id", ClassListChange.selectedIsStale("o3", "2D", list))
        assertFalse("nazwa nieznana - tylko obecność", ClassListChange.selectedIsStale("o3", null, list))
    }

    @Test fun shortOrMissingListIsNotTrusted() {
        assertFalse(ClassListChange.selectedIsStale("o9", "4D", null))
        assertFalse(ClassListChange.selectedIsStale("o9", "4D", list.take(2)))
    }
}
