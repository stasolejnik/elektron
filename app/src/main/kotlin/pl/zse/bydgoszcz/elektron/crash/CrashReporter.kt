package pl.zse.bydgoszcz.elektron.crash

import android.content.Context
import android.os.Build
import pl.zse.bydgoszcz.elektron.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Lokalne raporty awarii - bez Firebase Crashlytics, więc działa też w wersji F-Droid.
 * Przy awarii ślad stosu i ostatnie logi aplikacji trafiają do pliku w pamięci aplikacji. Przy
 * następnym uruchomieniu MainActivity pokazuje raport (kopiowanie, e-mail, GitHub).
 * Bez zgody użytkownika nic nie wychodzi.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                val report = CrashReport.format(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    flavor = BuildConfig.FLAVOR,
                    androidVersion = Build.VERSION.RELEASE ?: "?",
                    sdkInt = Build.VERSION.SDK_INT,
                    device = "${Build.MANUFACTURER} ${Build.MODEL}",
                    time = Diagnostics.now(),
                    stackTrace = stack,
                    thread = thread.name,
                    details = Diagnostics.deviceState(appContext),
                    // Logi tuż sprzed awarii - często pokazują przyczynę (np. nietypowe dane ze strony).
                    logs = Diagnostics.appLogs(timeoutMs = 1_000)
                )
                // Zapis synchroniczny - proces za chwilę zostanie zakończony.
                file(appContext).writeText(report)
            } catch (_: Throwable) {
                // Raport jest dodatkiem - nigdy nie może przeszkodzić w normalnej obsłudze awarii.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Raport z poprzedniej awarii albo null. */
    fun pending(context: Context): String? =
        try { file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } }
        catch (_: Exception) { null }

    fun clear(context: Context) {
        try { file(context).delete() } catch (_: Exception) { }
    }
}
