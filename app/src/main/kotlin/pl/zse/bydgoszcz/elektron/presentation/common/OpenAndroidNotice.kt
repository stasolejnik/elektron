package pl.zse.bydgoszcz.elektron.presentation.common

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Jednorazowy komunikat o weryfikacji deweloperów Androida (Google, od 2027 roku) i o tym, że eLektron nie będzie
 * rejestrowany: na 16 dni przed [DEADLINE] i później, przy pierwszym otwarciu aplikacji od tego dnia. Data według [pl.zse.bydgoszcz.elektron.domain.util.AppClock],
 * więc symulowany czas z trybu dewelopera też go wywoła. Tryb dewelopera może też wymusić pokazanie
 * przy następnym otwarciu ([simulate]) niezależnie od daty.
 */
object OpenAndroidNotice {
    val DEADLINE: LocalDate = LocalDate.of(2027, 1, 1)
    const val DAYS_BEFORE = 16L
    const val URL = "https://keepandroidopen.org/"

    private const val PREFS = "open_android_notice"
    private const val KEY_SHOWN = "shown"
    private const val KEY_SIMULATE = "simulate"

    /** Od tego dnia (16 grudnia 2026) komunikat czeka na pierwsze otwarcie aplikacji. */
    val firstDay: LocalDate get() = DEADLINE.minusDays(DAYS_BEFORE)

    fun isDue(today: LocalDate, shown: Boolean, simulate: Boolean): Boolean =
        !shown && (simulate || !today.isBefore(firstDay))

    fun isDue(context: Context, today: LocalDate): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return isDue(today, prefs.getBoolean(KEY_SHOWN, false), prefs.getBoolean(KEY_SIMULATE, false))
    }

    fun markShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SHOWN, true).remove(KEY_SIMULATE).apply()
    }

    /** Tryb dewelopera: komunikat pojawi się przy następnym otwarciu aplikacji, jak po nadejściu daty. */
    fun simulate(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SHOWN, false).putBoolean(KEY_SIMULATE, true).apply()
    }
}

/**
 * Okno komunikatu: co planuje Google, że twórca eLektronu nie zamierza się do tego stosować (i dlaczego), co to znaczy
 * dla użytkownika i odnośnik do keepandroidopen.org.
 */
@Composable
fun OpenAndroidDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val body = MaterialTheme.typography.bodyMedium
    // Polski skład: jednoliterowe spójniki i przyimki ("z", "a", "i", "o", "w") nie zostają same na końcu linii.
    fun glue(text: String) = text.replace(Regex("(?<=\\s|^)([aiouwzAIOUWZ]) "), "$1\u00A0")
    @Composable fun Heading(text: String) =
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    @Composable fun Point(text: String) = Row {
        Text("•", style = body, modifier = Modifier.padding(end = 8.dp))
        Text(glue(text), style = body)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google ogranicza otwartość Androida") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(glue(
                    "Od 2027 roku Google stopniowo wprowadza na telefonach z usługami Google wymóg weryfikacji: " +
                        "aplikację da się zwyczajnie zainstalować tylko wtedy, gdy jej twórca zarejestruje się " +
                        "w Google, także jeśli aplikacja nie pochodzi ze Sklepu Play."),
                    style = body
                )
                Heading("eLektron nie będzie zarejestrowany")
                Text(glue("Twórca eLektronu nie zamierza stosować się do tych zasad ani rejestrować aplikacji w Google. Dlaczego:"),
                    style = body)
                Point("eLektron to wolne oprogramowanie z publicznym kodem - każdy może sprawdzić, co robi aplikacja, " +
                    "bez pośrednictwa Google.")
                Point("Rejestracja wymaga przekazania Google danych osobowych twórcy, a o tym, jakie aplikacje można " +
                    "zainstalować na własnym telefonie, decydowałaby jedna firma.")
                Point("eLektron jest dostępny z GitHuba i z F-Droid, poza Sklepem Play, i tak ma zostać.")
                Heading("Co to znaczy dla Ciebie")
                Text(glue(
                    "Do czasu, gdy wymóg obejmie Polskę, nic się nie zmienia. Potem instalacja i aktualizacje eLektronu " +
                        "będą wymagały jednorazowego włączenia instalowania aplikacji od niezweryfikowanych twórców " +
                        "w opcjach programisty (z ponownym uruchomieniem telefonu i dobą oczekiwania) albo instalacji " +
                        "z komputera przez ADB."),
                    style = body
                )
                Text(glue(
                    "Więcej o zmianie i o tym, jak możesz pomóc zachować otwartego Androida, przeczytasz na " +
                        "keepandroidopen.org."),
                    style = body
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { SafeUrls.open(context, OpenAndroidNotice.URL); onDismiss() }) { Text("Dowiedz się więcej") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } }
    )
}
