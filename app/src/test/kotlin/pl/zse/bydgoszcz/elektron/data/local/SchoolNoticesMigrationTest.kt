package pl.zse.bydgoszcz.elektron.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 1.0.0 (baza w wersji 3) -> 1.0.1: nowa tabela informacji szkoły, dotychczasowe dane zostają. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchoolNoticesMigrationTest {
    @Test fun version3DatabaseGainsNoticesTableWithoutLosingSubstitutions() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-notices.db"
        ctx.deleteDatabase(name)
        try {
            val schema = JSONObject(javaClass.getResource("/pl.zse.bydgoszcz.elektron.data.local.AppDatabase/3.json")!!.readText())
                .getJSONObject("database")
            ctx.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.getJSONArray("indices")
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql")
                        .replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                old.execSQL("INSERT INTO room_master_table VALUES(42, ?)", arrayOf(schema.getString("identityHash")))
                old.execSQL("INSERT INTO substitutions VALUES ('s1', 20742, 3, '1D', NULL, '105', 'A.Nowak', NULL, 'J.Kowalski', NULL)")
                old.version = 3
            }
            val upgraded = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
                .allowMainThreadQueries().addMigrations(*DatabaseMigrations.ALL).build()
            try {
                assertEquals("s1", upgraded.substitutionDao().getFromDay(0).single().id)
                assertTrue(upgraded.schoolNoticeDao().observeFrom(0).first().isEmpty())
                upgraded.schoolNoticeDao().upsert(SchoolNoticeEntity(20742, "Apel"))
                assertEquals("Apel", upgraded.schoolNoticeDao().get(20742)?.text)
            } finally { upgraded.close() }
        } finally { ctx.deleteDatabase(name) }
    }
}
