package pl.zse.bydgoszcz.elektron.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import pl.zse.bydgoszcz.elektron.MainActivity
import pl.zse.bydgoszcz.elektron.domain.model.LessonLinks
import pl.zse.bydgoszcz.elektron.domain.model.LessonTarget

/** Klucz extra rozpoznawany przez MainActivity.resolveDeepLink (ten sam co w skrótach). */
private val ShortcutKey = ActionParameters.Key<String>("elektron_shortcut")

/**
 * Tło widżetu: zaokrąglona karta (22 dp jak widżety iOS), dotknięcie otwiera stronę główną.
 * actionStartActivity z jawnie podanymi parametrami — to jednoznacznie wybiera stabilne
 * przeciążenie (bez parametru Intent, na którym wyłożyły się poprzednie widżety).
 */
@Composable
fun WidgetContainer(target: String = "dashboard", content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetColors.background)
            .cornerRadius(22.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>(actionParametersOf(ShortcutKey to target))),
        content = content
    )
}

@Composable
fun WidgetMessage(text: String) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = TextStyle(color = WidgetColors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        )
    }
}

private val LessonKey = ActionParameters.Key<String>(LessonLinks.EXTRA_LESSON)

/**
 * Wiersz widżetu Zastępstwa: plan na dzień zastępstwa i szczegóły tej lekcji.
 * Cel w extras, a mimo to każdy wiersz otwiera swoją lekcję: Glance nadaje każdej akcji
 * unikalny identyfikator intencji (PendingIntent porównuje intencje bez extras), a wiersze
 * listy (LazyColumn) mają osobne fill-in intenty. Bez własnego data URI: na Androidzie < 10
 * Glance zapisuje tam swój identyfikator (minSdk 26).
 */
fun openLessonAction(target: LessonTarget): Action =
    actionStartActivity<MainActivity>(actionParametersOf(LessonKey to LessonLinks.deepLink(target)))

/** Otwarcie aplikacji na stronie głównej — także z wierszy list w widżetach. */
fun openAppAction(): Action =
    actionStartActivity<MainActivity>(actionParametersOf(ShortcutKey to "dashboard"))
