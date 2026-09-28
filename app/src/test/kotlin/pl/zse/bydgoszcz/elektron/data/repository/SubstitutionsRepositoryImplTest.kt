package pl.zse.bydgoszcz.elektron.data.repository

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
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubstitutionsRepositoryImplTest {

    private class FakeSource(var items: List<SubstitutionDto> = emptyList()) : SubstitutionsSource {
        override suspend fun fetchSubstitutions() = items
    }

    private val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val today = LocalDate.now()
    private lateinit var db: AppDatabase
    private val source = FakeSource()
    private lateinit var repo: SubstitutionsRepositoryImpl

    private fun dto(date: LocalDate, lesson: Int) =
        SubstitutionDto(date.format(fmt), "Y.Nauczyciel", lesson, "2D", null, "316", "X.Zastępca", null)

    @Before fun setUp() {
        db = inMemoryDb()
        repo = SubstitutionsRepositoryImpl(source, db.substitutionDao(), db)
    }

    @After fun tearDown() = db.close()

    @Test
    fun cancelledSubstitutionDisappearsAfterSync() = runTest {
        // Bug audytu #6: zastępstwo skasowane na stronie szkoły zostawało w aplikacji.
        source.items = listOf(dto(today.plusDays(1), 1), dto(today.plusDays(1), 2))
        repo.syncAll()
        source.items = listOf(dto(today.plusDays(1), 2))
        repo.syncAll()
        assertEquals(listOf(2), repo.getAllFrom(today).map { it.lessonNumber })
    }

    @Test
    fun pastSubstitutionsSurviveSync() = runTest {
        source.items = listOf(dto(today.minusDays(1), 3), dto(today.plusDays(1), 1))
        repo.syncAll()
        source.items = listOf(dto(today.plusDays(1), 1))
        repo.syncAll()
        assertTrue(repo.getAllFrom(today.minusDays(1)).any { it.date == today.minusDays(1) })
    }

    @Test
    fun emptyFetchDoesNotWipeData() = runTest {
        // Pusta odpowiedź (np. awaria strony) nie może skasować znanych zastępstw.
        source.items = listOf(dto(today.plusDays(1), 1))
        repo.syncAll()
        source.items = emptyList()
        repo.syncAll()
        assertEquals(1, repo.getAllFrom(today).size)
    }
}
