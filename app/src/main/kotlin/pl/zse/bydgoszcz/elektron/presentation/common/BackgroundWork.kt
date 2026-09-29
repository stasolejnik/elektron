package pl.zse.bydgoszcz.elektron.presentation.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Optymalizacja baterii. Część producentów (Xiaomi/HyperOS, Samsung, Huawei, Oppo) ubija
 * aplikacje w tle - wtedy widżety przestają się odświeżać, a powiadomienia przychodzą
 * z opóźnieniem. Bez specjalnych uprawnień: otwieramy systemową listę optymalizacji,
 * gdzie użytkownik sam przełącza eLektron na "Bez ograniczeń".
 */
object BackgroundWork {

    /** Poradniki dla konkretnych producentów (MIUI/HyperOS mają dodatkowy "Autostart"). */
    const val GUIDE_URL = "https://dontkillmyapp.com/"

    fun isUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            // Niektóre nakładki nie mają listy optymalizacji - wtedy ekran aplikacji.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        )
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) { }
        }
    }
}
