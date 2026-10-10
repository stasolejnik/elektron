package pl.zse.bydgoszcz.elektron.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AnnouncementDateTest {
    @Test fun rfc822VariantsAreRead() {
        val expected = Instant.parse("2026-10-02T08:00:00Z")
        assertEquals(expected, AnnouncementMapper.parsePubDate("Fri, 02 Oct 2026 10:00:00 +0200"))
        // Jednocyfrowy dzień i strefa "GMT" - dawniej ogłoszenie znikało bez śladu.
        assertEquals(expected, AnnouncementMapper.parsePubDate("Fri, 2 Oct 2026 10:00:00 +0200"))
        assertEquals(expected, AnnouncementMapper.parsePubDate("Fri, 2 Oct 2026 08:00:00 GMT"))
        assertEquals(expected, AnnouncementMapper.parsePubDate("2026-10-02T08:00:00Z"))
        assertNull(AnnouncementMapper.parsePubDate("wczoraj"))
        assertNull(AnnouncementMapper.parsePubDate(""))
    }

    @Test fun coverImagesOnlyFromSchoolServers() {
        org.junit.Assert.assertEquals("https://zse.bydgoszcz.pl/images/a.jpg", AnnouncementMapper.schoolImageUrl("http://zse.bydgoszcz.pl/images/a.jpg"))
        org.junit.Assert.assertEquals("https://zse.edu.bydgoszcz.pl/a.jpg", AnnouncementMapper.schoolImageUrl("//zse.edu.bydgoszcz.pl/a.jpg"))
        org.junit.Assert.assertEquals("https://zse.bydgoszcz.pl/images/b.png", AnnouncementMapper.schoolImageUrl("/images/b.png"))
        org.junit.Assert.assertNull(AnnouncementMapper.schoolImageUrl("https://tracker.example/pixel.gif"))
        org.junit.Assert.assertNull(AnnouncementMapper.schoolImageUrl("https://zse.bydgoszcz.pl.evil.example/a.jpg"))
        org.junit.Assert.assertNull(AnnouncementMapper.schoolImageUrl("javascript:alert(1)"))
    }
}
