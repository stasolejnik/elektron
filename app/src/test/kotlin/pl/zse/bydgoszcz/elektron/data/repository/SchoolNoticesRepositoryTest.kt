package pl.zse.bydgoszcz.elektron.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase
import pl.zse.bydgoszcz.elektron.data.local.SchoolNoticeEntity
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsPage
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Informacje szkoły: zapis per dzień strony, znikanie po usunięciu ze strony, sprzątanie starych. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchoolNoticesRepositoryTest {
    private val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val tomorrow = LocalDate.now().plusDays(1)
    private val dayAfter = tomorrow.plusDays(1)
    private var page = SubstitutionsPage(emptySet(), emptyList())
    private lateinit var db: AppDatabase
    private lateinit var repo: SubstitutionsRepositoryImpl

    @Before fun setUp() {
        db = inMemoryDb()
        repo = SubstitutionsRepositoryImpl(object : SubstitutionsSource {
            override suspend fun fetchSubstitutions() = page.items
            override suspend fun fetchPage() = page
        }, db.substitutionDao(), db)
    }

    @After fun tearDown() = db.close()

    private fun dto(d: LocalDate) = SubstitutionDto(d.format(fmt), "Y.Nauczyciel", 1, "2D", null, "316", "X.Zastępca", null)

    @Test fun noticeIsStoredPerDayEvenWithoutEntriesAndDisappearsWhenRemoved() = runTest {
        page = SubstitutionsPage(setOf(tomorrow.format(fmt), dayAfter.format(fmt)), listOf(dto(tomorrow)),
            notices = mapOf(tomorrow.format(fmt) to "Apel o 8:00\nW auli", dayAfter.format(fmt) to "Dzień sportu"))
        repo.syncAll()
        assertEquals(mapOf(tomorrow to "Apel o 8:00\nW auli", dayAfter to "Dzień sportu"), repo.observeNotices(LocalDate.now()).first())
        // Szkoła usunęła tekst z jutra; pojutrze bez zmian.
        page = page.copy(notices = mapOf(dayAfter.format(fmt) to "Dzień sportu"))
        repo.syncAll()
        assertEquals(mapOf(dayAfter to "Dzień sportu"), repo.observeNotices(LocalDate.now()).first())
    }

    @Test fun failedOrEmptyPageKeepsNoticesForOffline() = runTest {
        page = SubstitutionsPage(setOf(tomorrow.format(fmt)), emptyList(), notices = mapOf(tomorrow.format(fmt) to "Apel"))
        repo.syncAll()
        page = SubstitutionsPage(emptySet(), emptyList())          // nierozpoznana / pusta odpowiedź
        repo.syncAll()
        assertEquals(mapOf(tomorrow to "Apel"), repo.observeNotices(LocalDate.now()).first())
    }

    @Test fun oldNoticesAreCleanedWithSubstitutions() = runTest {
        val old = LocalDate.now().minusDays(70)
        db.schoolNoticeDao().upsert(SchoolNoticeEntity(old.toEpochDay(), "Dawno"))
        page = SubstitutionsPage(setOf(tomorrow.format(fmt)), listOf(dto(tomorrow)))
        repo.syncAll()
        assertTrue(repo.observeNotices(old).first().isEmpty())
    }
}
