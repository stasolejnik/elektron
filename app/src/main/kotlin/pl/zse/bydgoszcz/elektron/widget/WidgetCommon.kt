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

/** Otwarcie aplikacji na stronie głównej — także z wierszy list w widżetach. */
fun openAppAction(): Action =
    actionStartActivity<MainActivity>(actionParametersOf(ShortcutKey to "dashboard"))
