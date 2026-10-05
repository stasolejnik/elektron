package pl.zse.bydgoszcz.elektron.presentation.transit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class TransitTimeTest {
    private fun at(text: String) = ZonedDateTime.parse(text).toInstant().toEpochMilli()
    @Test fun midnightAndOldResultsAreClearlyDatedInWarsawTime() {
        val now = at("2026-10-05T23:50:00+02:00")
        assertEquals("23:55", formatTransitTime(at("2026-10-05T23:55:00+02:00"), now))
        assertEquals("jutro 00:15", formatTransitTime(at("2026-10-06T00:15:00+02:00"), now))
        assertEquals("4.10 22:00", formatTransitTime(at("2026-10-04T22:00:00+02:00"), now))
    }
    @Test fun dstUsesLocalTransitTimeInsteadOfDeviceTimezone() {
        val now = at("2026-10-25T00:10:00+02:00")
        assertEquals("02:30", formatTransitTime(at("2026-10-25T02:30:00+01:00"), now))
    }
}
