package pl.zse.bydgoszcz.elektron.work

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 34])
class NotificationChannelsTest {
    @Test fun channelsAreCreatedOnEverySupportedAndroidVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LocalNotificationSink(context, FakeSettings())
        val manager = context.getSystemService(NotificationManager::class.java)
        val group = manager.notificationChannelGroups.single { it.name == "eLektron" }
        val channels = manager.notificationChannels.filter { it.group == group.id }
        assertEquals(setOf("Zastępstwa", "Ogłoszenia", "Notatki do lekcji", "Przypomnienia o lekcjach"), channels.map { it.name.toString() }.toSet())
        if (Build.VERSION.SDK_INT >= 28) assertEquals("Powiadomienia z aplikacji eLektron", group.description)
        // Recreating the sink must not duplicate or replace the user's channel settings.
        LocalNotificationSink(context, FakeSettings())
        assertEquals(channels.map { it.id }.toSet(), manager.notificationChannels.filter { it.group == group.id }.map { it.id }.toSet())
    }
}
