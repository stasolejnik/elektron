package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Znaczniki z prawdziwych planów ZSE (wrzesień 2026). */
class JointGroupsTest {

    @Test fun yearAndLetters() {
        assertEquals("łączona z 1A", JointGroups.describe("#1AF", "1F Woj"))
        assertEquals("łączona z 1F", JointGroups.describe("#1AF", "1A Ivy"))
        assertEquals("łączona z 2D", JointGroups.describe("#2DI", "2I"))
    }

    @Test fun classList() {
        assertEquals("łączona z 5D", JointGroups.describe("5B,5D", "5B"))
    }

    @Test fun schoolCodesStayGeneric() {
        assertEquals("grupa łączona", JointGroups.describe("#RHJ", "4H ATOS"))
        assertEquals("grupa łączona", JointGroups.describe("#A_D", "1A Ivy"))
    }

    @Test fun notJoint() {
        assertNull(JointGroups.describe(null, "1A"))
        assertNull(JointGroups.describe("o12", "1A"))
    }
}
