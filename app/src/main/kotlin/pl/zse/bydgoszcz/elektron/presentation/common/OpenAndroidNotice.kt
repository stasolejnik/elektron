package pl.zse.bydgoszcz.elektron.presentation.common

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
 * Jednorazowy komunikat o weryfikacji deweloperów Androida (Google, od 2027 roku): na 14 dni przed
 * [DEADLINE] i później, przy pierwszym otwarciu aplikacji od tego dnia. Data według [pl.zse.bydgoszcz.elektron.domain.util.AppClock],
 * więc symulowany czas z trybu dewelopera też go wywoła. Tryb dewelopera może też wymusić pokazanie
 * przy następnym otwarciu ([simulate]) niezależnie od daty.
 */
object OpenAndroidNotice {
    val DEADLINE: LocalDate = LocalDate.of(2027, 1, 1)
    const val DAYS_BEFORE = 14L
    const val URL = "https://keepandroidopen.org/"

    private const val PREFS = "open_android_notice"
    private const val KEY_SHOWN = "shown"
    private const val KEY_SIMULATE = "simulate"

    /** Od tego dnia (18 grudnia 2026) komunikat czeka na pierwsze otwarcie aplikacji. */
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

/** Okno komunikatu: co planuje Google, co to znaczy dla eLektronu i odnośnik do keepandroidopen.org. */
@Composable
fun OpenAndroidDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google ogranicza otwartość Androida") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Od 2027 roku Google stopniowo wprowadza na telefonach z usługami Google wymóg weryfikacji: " +
                        "aplikację da się zwyczajnie zainstalować tylko wtedy, gdy jej twórca zarejestruje się " +
                        "w Google, także jeśli aplikacja nie pochodzi ze Sklepu Play.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Co to znaczy dla eLektronu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "eLektron jest dostępny tylko z GitHuba i z F-Droid, poza Sklepem Play. Gdy wymóg obejmie " +
                        "Polskę, a aplikacja nie będzie zarejestrowana w Google, instalacja i aktualizacje mogą " +
                        "wymagać dodatkowych kroków: włączenia opcji programisty, ponownego uruchomienia telefonu " +
                        "i odczekania doby albo instalacji z komputera przez ADB.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Więcej o zmianie i o tym, jak możesz pomóc zachować otwartego Androida, przeczytasz na " +
                        "keepandroidopen.org.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { SafeUrls.open(context, OpenAndroidNotice.URL); onDismiss() }) { Text("Dowiedz się więcej") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } }
    )
}
