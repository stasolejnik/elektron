package pl.zse.bydgoszcz.elektron.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.MainActivity
import pl.zse.bydgoszcz.elektron.domain.model.LessonLinks
import pl.zse.bydgoszcz.elektron.domain.model.LessonTarget
import java.time.LocalDate

/** Intencja wiersza widżetu Zastępstwa niesie cel w extras i w data URI; inna dla każdej lekcji. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LessonIntentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.now()
    private val target = LessonTarget(today.plusDays(1), 3)

    @Test
    fun carriesBothChannels() {
        val intent = lessonIntent(context, target)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(target, LessonLinks.parseDeepLink(intent.getStringExtra(LessonLinks.EXTRA_LESSON), today))
        assertEquals(LessonLinks.uri(target), intent.dataString)
        // To samo rozstrzygnięcie co w MainActivity, osobno dla każdego kanału.
        assertEquals(target, LessonLinks.resolveIntent(intent.getStringExtra(LessonLinks.EXTRA_LESSON), null, today).target)
        assertEquals(target, LessonLinks.resolveIntent(null, intent.dataString, today).target)
    }

    @Test
    fun differentLessonsAreDifferentIntents() {
        // PendingIntent porównuje intencje jak filterEquals (bez extras) - wiersze nie mogą być "równe".
        val other = lessonIntent(context, target.copy(lessonNumber = 4))
        val otherDay = lessonIntent(context, target.copy(date = target.date.plusDays(1)))
        assertFalse(lessonIntent(context, target).filterEquals(other))
        assertFalse(lessonIntent(context, target).filterEquals(otherDay))
    }
}
