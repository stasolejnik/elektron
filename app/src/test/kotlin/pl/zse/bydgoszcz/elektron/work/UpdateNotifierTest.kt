package pl.zse.bydgoszcz.elektron.work

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.BuildConfig
import pl.zse.bydgoszcz.elektron.domain.repository.AppUpdate
import pl.zse.bydgoszcz.elektron.domain.repository.UpdateRepository
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings

/** Powiadomienie o nowej wersji: tylko po włączeniu, raz na wersję, nigdy w wersji F-Droid. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun shown(): Notification? = manager.activeNotifications.firstOrNull { it.id == UpdateNotifier.NOTIFICATION_ID }?.notification

    private class Updates : UpdateRepository {
        val available = MutableStateFlow<AppUpdate?>(null)
        var checks = 0
        override val availableUpdate = available
        override suspend fun check(force: Boolean): Result<AppUpdate?> { checks++; assertFalse(force); return Result.success(available.value) }
        override suspend fun dismiss(versionName: String) { available.value = null }
    }

    @Test fun notifiesOncePerVersionOnlyWhenEnabled() = runBlocking {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val settings = FakeSettings()
        val updates = Updates().apply { available.value = AppUpdate("9.9.9", "https://github.com/stasolejnik/elektron/releases/tag/v9.9.9") }
        val notifier = UpdateNotifier(context, settings, updates)
        notifier.checkInBackground()
        assertNull("domyślnie wyłączone", shown())
        assertEquals(0, updates.checks)

        settings.updatesFlow.value = true
        notifier.checkInBackground()
        if (!BuildConfig.UPDATE_CHECK) {
            assertNull("F-Droid aktualizuje sam", shown())
            return@runBlocking
        }
        val n = requireNotNull(shown())
        assertEquals("Nowa wersja eLektronu: 9.9.9", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(UpdateNotifier.CHANNEL_UPDATES, n.channelId)
        manager.cancelAll()
        notifier.checkInBackground()
        assertNull("ta sama wersja tylko raz", shown())
        updates.available.value = AppUpdate("9.9.10", "https://github.com/stasolejnik/elektron/releases/tag/v9.9.10")
        notifier.checkInBackground()
        assertNotNull(shown())
    }
}
