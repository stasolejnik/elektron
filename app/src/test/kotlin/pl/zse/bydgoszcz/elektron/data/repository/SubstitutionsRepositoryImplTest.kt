package pl.zse.bydgoszcz.elektron.data.repository

import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsPage
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

    @Test
    fun todaySurvivesWhenSchoolPublishesTomorrow() = runTest {
        // Szkoła publikuje jutrzejsze zastępstwa w trakcie dzisiejszych lekcji (strona pokazuje
        // już tylko jutro) - dzisiejsze muszą zostać w planie.
        source.items = listOf(dto(today, 5))
        repo.syncAll()
        source.items = listOf(dto(today.plusDays(1), 1))
        repo.syncAll()
        assertEquals(listOf(today, today.plusDays(1)), repo.getAllFrom(today).map { it.date })
    }

    @Test
    fun dayWithAllSubstitutionsCancelledIsCleared() = runTest {
        // Strona z nagłówkiem dnia, ale bez wpisów - szkoła odwołała wszystkie zastępstwa.
        source.items = listOf(dto(today.plusDays(1), 1))
        repo.syncAll()
        val emptyDay = object : SubstitutionsSource {
            override suspend fun fetchSubstitutions() = emptyList<SubstitutionDto>()
            override suspend fun fetchPage() = SubstitutionsPage(setOf(today.plusDays(1).format(fmt)), emptyList())
        }
        SubstitutionsRepositoryImpl(emptyDay, db.substitutionDao(), db).syncAll()
        assertTrue(repo.getAllFrom(today).isEmpty())
    }

    private fun realPage(html: String): SubstitutionsPage {
        val doc = org.jsoup.Jsoup.parse(html, "https://zastepstwa.zse.bydgoszcz.pl/")
        val parsed = pl.zse.bydgoszcz.elektron.data.remote.parser.ZastepstwaParser.parseDetailed(doc)
        return SubstitutionsPage(pl.zse.bydgoszcz.elektron.data.remote.parser.ZastepstwaParser.pageDates(doc),
            parsed.items, parsed.incompleteDates)
    }

    @Test
    fun unreadableEntryKeepsPreviouslySavedDay() = runTest {
        // Zapisana strona z 01.10.2026, potem ta sama strona z celowo uszkodzonym wpisem 1D
        // (nietypowy zapis klasy). Dawniej dzień był czyszczony, a wpis 1D znikał z aplikacji.
        // Data strony przesunięta na dziś - starsze niż 60 dni wpisy repozytorium sprząta.
        val html = javaClass.getResourceAsStream("/zastepstwa/2026-10-01.html")!!.use { it.readBytes().toString(Charsets.UTF_8) }
            .replace("01.10.2026", today.format(fmt))
        var page = realPage(html)
        val pageSource = object : SubstitutionsSource {
            override suspend fun fetchSubstitutions() = page.items
            override suspend fun fetchPage() = page
        }
        val repo = SubstitutionsRepositoryImpl(pageSource, db.substitutionDao(), db)
        val day = today
        repo.syncAll()
        assertEquals(32, repo.getAllFrom(day).count { it.date == day })

        page = realPage(html.replace("1 D(2) - Zajęcia Świetlicowe", "1Dg2 - Zajęcia Świetlicowe"))
        assertTrue(repo.syncAll().exceptionOrNull() is pl.zse.bydgoszcz.elektron.domain.model.IncompleteSchoolDataException)
        val after = repo.getAllFrom(day).filter { it.date == day }
        assertEquals(32, after.size)
        assertTrue(after.any { it.classShortName == "1D" && it.lessonNumber == 7 })
    }

    @Test
    fun completeDayIsStillReplaced() = runTest {
        // Kontrola: przy komplecie wpisów dzień nadal jest zastępowany (odwołane znikają).
        val d = today.plusDays(1)
        source.items = listOf(dto(d, 1), dto(d, 2))
        repo.syncAll()
        val pageSource = object : SubstitutionsSource {
            override suspend fun fetchSubstitutions() = listOf(dto(d, 2))
            override suspend fun fetchPage() = SubstitutionsPage(setOf(d.format(fmt)), listOf(dto(d, 2)), incompleteDates = setOf(d.format(fmt)))
        }
        // Niekompletny dzień: nic nie kasujemy.
        SubstitutionsRepositoryImpl(pageSource, db.substitutionDao(), db).syncAll()
        assertEquals(listOf(1, 2), repo.getAllFrom(d).map { it.lessonNumber })
        // Kompletny: zastąpiony.
        source.items = listOf(dto(d, 2))
        repo.syncAll()
        assertEquals(listOf(2), repo.getAllFrom(d).map { it.lessonNumber })
    }

    private fun watchWrites(table: String) {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE IF NOT EXISTS audit_writes (value INTEGER)")
        for (operation in listOf("INSERT", "UPDATE", "DELETE")) {
            sql.execSQL("CREATE TRIGGER audit_${table}_$operation AFTER $operation ON $table BEGIN INSERT INTO audit_writes VALUES (1); END")
        }
    }

    @Test fun identicalSyncDoesNotRewriteTables() = runTest {
        source.items = listOf(dto(today, 1))
        assertTrue(repo.syncAll().isSuccess)
        watchWrites("substitutions")
        assertTrue(repo.syncAll().isSuccess)
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM audit_writes").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }
}
