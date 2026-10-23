package pl.zse.bydgoszcz.elektron.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import pl.zse.bydgoszcz.elektron.BuildConfig
import pl.zse.bydgoszcz.elektron.R
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.UpdateRepository
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Powiadomienie o nowej wersji (Ustawienia -> Aktualizacje, domyślnie wyłączone; tylko wersja
 * z GitHuba - F-Droid aktualizuje sam). Sprawdzanie przy synchronizacji w tle, ale GitHub pytany
 * najwyżej co 12 godzin (UpdateRepository). Jedno powiadomienie na wersję; wersja odłożona
 * przyciskiem "Później" na stronie głównej nie wraca w powiadomieniu.
 */
@Singleton
class UpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val updates: UpdateRepository
) {
    suspend fun checkInBackground() {
        if (!BuildConfig.UPDATE_CHECK || !settings.notifyUpdates.first()) return
        updates.check(force = false)
        val update = runCatchingCancellable { updates.availableUpdate.first() }.getOrNull() ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_NOTIFIED, null) == update.versionName) return
        if (!canPost()) return
        createChannel()
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return
        val n = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_elektron)
            .setContentTitle("Nowa wersja eLektronu: ${update.versionName}")
            .setContentText("Dotknij, aby zobaczyć zmiany i zaktualizować.")
            .setContentIntent(PendingIntent.getActivity(context, REQUEST_OPEN, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
            prefs.edit().putString(KEY_NOTIFIED, update.versionName).apply()
            Log.i(TAG, "Powiadomienie o wersji ${update.versionName}")
        } catch (e: SecurityException) { Log.w(TAG, "Brak uprawnień do powiadomień", e) }
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Kanał tylko w wersji z GitHuba i dopiero przy pierwszym powiadomieniu. */
    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Aktualizacje", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Nowa wersja aplikacji (Ustawienia -> Aktualizacje)."
                group = LocalNotificationSink.GROUP_ELEKTRON
            })
    }

    companion object {
        private const val TAG = "UpdateNotifier"
        const val CHANNEL_UPDATES = "elektron_updates"
        const val NOTIFICATION_ID = 0x55504454
        private const val REQUEST_OPEN = 4301
        private const val PREFS = "update_notifier"
        private const val KEY_NOTIFIED = "notified_version"
    }
}
