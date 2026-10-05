package pl.zse.bydgoszcz.elektron.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransitTest {
    @Test fun catalogGroupsPlatformsAndRejectsInvalidCoordinates() {
        val stops = TransitStopParser.parse("""[
            {"id":9,"nazwa":"Rondo Jagiellonów","lat":53.12,"lon":18.00},
            {"id":10,"nazwa":"Rondo Jagiellonów","lat":53.13,"lon":18.01},
            {"id":9,"nazwa":"Duplikat","lat":53.12,"lon":18.00},
            {"id":11,"nazwa":"UKW","lat":53.14,"lon":18.02},
            {"id":12,"nazwa":"Błąd","lat":153,"lon":18.00}
        ]""")
        assertEquals(2, stops.size)
        val complex = stops.first { it.name == "Rondo Jagiellonów" }
        assertEquals(listOf("9", "10"), complex.stopIds)
        assertEquals(53.125, complex.latitude, 0.00001)
        assertEquals("10,9", complex.key)
    }
    @Test fun emptyOrMalformedCatalogIsAnError() {
        for (json in listOf("[]", "{}", "[{\"id\":1}]")) {
            try { TransitStopParser.parse(json); fail("Invalid catalog must not replace a cached catalog") }
            catch (_: Exception) { }
        }
    }
    @Test fun enablingRequiresDestinationAndClearingAlsoDisables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "transit-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val repo = TransitPreferencesRepository(store)
        try {
            assertFalse(repo.preferences.first().visible)
            try { repo.setEnabled(true); fail("A destination must be chosen first") }
            catch (_: IllegalArgumentException) { }
            val destination = TransitDestination("Rondo Jagiellonów", listOf("9", "10"), 53.125, 18.005)
            repo.select(destination)
            assertEquals(destination, repo.preferences.first().destination)
            assertFalse(repo.preferences.first().enabled)
            repo.setEnabled(true)
            assertTrue(repo.preferences.first().visible)
            repo.setEnabled(false)
            assertEquals(destination, repo.preferences.first().destination)
            repo.setEnabled(true)
            store.edit { it[stringPreferencesKey("destination")] = "invalid" }
            assertFalse("Corrupted destination must hide the tab", repo.preferences.first().visible)
            repo.select(destination)
            repo.clearDestination()
            assertNull(repo.preferences.first().destination)
            assertFalse(repo.preferences.first().visible)
        } finally { scope.cancel(); scope.coroutineContext[Job]!!.join(); file.delete() }
    }
    @Test fun persistenceSurvivesRepositoryRestart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "transit-restart-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var repo = TransitPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        val destination = TransitDestination("Łęgnowo", listOf("99"), 53.1, 18.1)
        try {
            repo.select(destination); repo.setEnabled(true)
            val origin = TransitDestination("Jagiellońska - Łużycka", listOf("24"), 53.12194, 18.02764)
            repo.selectOrigin(origin)
            scope.cancel(); scope.coroutineContext[Job]!!.join()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repo = TransitPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            assertEquals(destination, repo.preferences.first().destination)
            assertEquals(origin, repo.preferences.first().preferredOrigin)
            assertTrue(repo.preferences.first().visible)
            repo.selectOrigin(null)
            assertNull(repo.preferences.first().preferredOrigin)
            assertTrue(repo.preferences.first().visible)
        } finally { scope.cancel(); scope.coroutineContext[Job]!!.join(); file.delete() }
    }
    @Test fun recentStopsAreSeparateLimitedDeduplicatedAndPersistWithTransferPreference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "transit-recents-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var repo = TransitPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        val stops = (1..7).map { TransitDestination("Stop $it", listOf(it.toString()), 53.123, 18.027) }
        try {
            assertFalse(repo.preferences.first().allowTransfers)
            stops.forEach { repo.select(it); repo.selectOrigin(it) }
            assertEquals(listOf("7", "6", "5", "4", "3"), repo.preferences.first().recentDestinations.map { it.key })
            repo.select(stops[3]); repo.selectOrigin(stops[5]); repo.setAllowTransfers(true)
            assertEquals(listOf("4", "7", "6", "5", "3"), repo.preferences.first().recentDestinations.map { it.key })
            assertEquals(listOf("6", "7", "5", "4", "3"), repo.preferences.first().recentOrigins.map { it.key })
            scope.cancel(); scope.coroutineContext[Job]!!.join()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repo = TransitPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            val restored = repo.preferences.first()
            assertTrue(restored.allowTransfers)
            assertEquals(5, restored.recentDestinations.size)
            assertEquals("4", restored.recentDestinations.first().key)
            assertEquals("6", restored.recentOrigins.first().key)
            repo.clearDestination(); repo.selectOrigin(null)
            assertEquals(restored.recentDestinations, repo.preferences.first().recentDestinations)
            assertEquals(restored.recentOrigins, repo.preferences.first().recentOrigins)
        } finally { scope.cancel(); scope.coroutineContext[Job]!!.join(); file.delete() }
    }
}
