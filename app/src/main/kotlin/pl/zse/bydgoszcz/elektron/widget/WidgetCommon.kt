package pl.zse.bydgoszcz.elektron.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartActivity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.action.clickable
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
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
import pl.zse.bydgoszcz.elektron.ElektronActivity
import pl.zse.bydgoszcz.elektron.domain.model.LessonLinks
import pl.zse.bydgoszcz.elektron.domain.model.LessonTarget

/** Klucz extra rozpoznawany przez ElektronActivity.resolveDeepLink (ten sam co w skrótach). */
private val ShortcutKey = ActionParameters.Key<String>("elektron_shortcut")

/**
 * Tło widżetu: zaokrąglona karta (22 dp jak widżety iOS), dotknięcie otwiera stronę główną
 * albo [action] (np. lekcję pokazywaną w widżecie Następna lekcja) z opisem [description].
 * actionStartActivity z jawnie podanymi parametrami — to jednoznacznie wybiera stabilne
 * przeciążenie (bez parametru Intent, na którym wyłożyły się poprzednie widżety).
 */
@Composable
fun WidgetContainer(
    target: String = "dashboard",
    action: Action? = null,
    description: String? = null,
    content: @Composable () -> Unit
) {
    var modifier = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(WidgetColors.background)
        .cornerRadius(22.dp)
        .padding(14.dp)
        .clickable(action ?: actionStartActivity<ElektronActivity>(actionParametersOf(ShortcutKey to target)))
    if (description != null) modifier = modifier.semantics { contentDescription = description }
    Box(modifier = modifier, content = content)
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

/**
 * Wiersz widżetu Zastępstwa: plan na dzień zastępstwa i szczegóły tej lekcji.
 * Cel dwoma kanałami w samej intencji: extra [LessonLinks.EXTRA_LESSON] i data URI
 * ([LessonLinks.uri]) - ElektronActivity przyjmuje oba (LessonLinks.resolveIntent). Data URI jest
 * inne dla każdej lekcji, więc PendingIntenty wierszy (porównywane bez extras) się nie sklejają.
 * Intencja jawna (komponent ElektronActivity) - przeciążenie actionStartActivity(Intent) dostaje
 * gotową intencję i niczego w niej nie zgaduje.
 */
fun lessonIntent(context: Context, target: LessonTarget): Intent =
    Intent(context, ElektronActivity::class.java)
        .setData(Uri.parse(LessonLinks.uri(target)))
        .putExtra(LessonLinks.EXTRA_LESSON, LessonLinks.deepLink(target))

fun openLessonAction(context: Context, target: LessonTarget): Action =
    actionStartActivity(lessonIntent(context, target))

/** Otwarcie sekcji aplikacji (np. "substitutions") - ten sam klucz co skróty. */
fun openSectionAction(section: String): Action =
    actionStartActivity<ElektronActivity>(actionParametersOf(ShortcutKey to section))

/** Otwarcie aplikacji na stronie głównej — także z wierszy list w widżetach. */
fun openAppAction(): Action =
    actionStartActivity<ElektronActivity>(actionParametersOf(ShortcutKey to "dashboard"))
