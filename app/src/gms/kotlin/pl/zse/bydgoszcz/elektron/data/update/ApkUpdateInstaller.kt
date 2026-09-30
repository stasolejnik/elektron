package pl.zse.bydgoszcz.elektron.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.zse.bydgoszcz.elektron.BuildConfig
import pl.zse.bydgoszcz.elektron.domain.repository.AppUpdate
import pl.zse.bydgoszcz.elektron.domain.repository.InstallState
import pl.zse.bydgoszcz.elektron.domain.repository.UpdateInstaller
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wariant gms (GitHub): pobiera APK nowej wersji z GitHub Releases i otwiera systemowy
 * instalator. Zanim cokolwiek zainstaluje, sprawdza:
 *  - SHA-256 pliku z sumą podaną przez GitHuba (jeśli wydanie ją ma),
 *  - że plik to eLektron (ta sama nazwa pakietu) w nowszej wersji niż zainstalowana.
 * Podpis sprawdza Android - APK podpisany innym kluczem się nie zainstaluje.
 */
@Singleton
class ApkUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    client: OkHttpClient
) : UpdateInstaller {

    override val supported: Boolean = true

    // Bez cache HTTP (20 MB APK nie ma tam czego szukać) i z dłuższym czasem na odczyt.
    private val http: OkHttpClient = client.newBuilder()
        .cache(null)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    override val state: StateFlow<InstallState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var ready: Pair<File, String>? = null   // pobrany plik i jego wersja

    private val dir: File get() = File(context.cacheDir, "updates")

    override fun start(update: AppUpdate) {
        val url = update.apkUrl ?: run {
            _state.value = InstallState.Failed("To wydanie nie ma pliku APK.")
            return
        }
        if (job?.isActive == true) return
        ready?.let { (file, version) ->
            if (version == update.versionName && file.exists()) { install(); return }
        }
        job = scope.launch {
            _state.value = InstallState.Downloading(update.versionName, if (update.sizeBytes > 0) 0f else null)
            try {
                val file = download(update, url)
                ready = file to update.versionName
                install()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Aktualizacja nie powiodła się", e)
                _state.value = InstallState.Failed(e.message ?: "Nie udało się pobrać aktualizacji.")
                runCatching { dir.listFiles()?.forEach { it.delete() } }
            }
        }
    }

    private fun download(update: AppUpdate, url: String): File {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }          // stare pobrania
        val part = File(dir, "download.part")
        val target = File(dir, "eLektron-${update.versionName.replace(Regex("[^0-9A-Za-z.-]"), "")}.apk")
        val digest = MessageDigest.getInstance("SHA-256")

        http.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Serwer GitHuba odpowiedział błędem (HTTP ${resp.code}).")
            val body = resp.body ?: throw IOException("Pusta odpowiedź serwera.")
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes.takeIf { it > 0 }
            var done = 0L
            var lastPercent = -1
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (!scope.isActive) throw CancellationException()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total != null) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = InstallState.Downloading(update.versionName, (done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }

        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (update.sha256 != null && !sha.equals(update.sha256, ignoreCase = true)) {
            part.delete()
            throw IOException("Pobrany plik jest uszkodzony (inna suma SHA-256). Spróbuj ponownie.")
        }
        val info = context.packageManager.getPackageArchiveInfo(part.path, 0)
        if (info == null || info.packageName != context.packageName) {
            part.delete()
            throw IOException("Pobrany plik nie jest aplikacją eLektron.")
        }
        if (PackageInfoCompat.getLongVersionCode(info) <= BuildConfig.VERSION_CODE) {
            part.delete()
            throw IOException("Pobrany plik nie zawiera nowszej wersji.")
        }
        if (!part.renameTo(target)) throw IOException("Nie udało się zapisać pliku.")
        return target
    }

    override fun install() {
        val (file, version) = ready ?: return
        if (!file.exists()) {
            ready = null
            _state.value = InstallState.Failed("Pobrany plik zniknął - pobierz aktualizację ponownie.")
            return
        }
        // Android 8+: zgoda "Instalowanie nieznanych aplikacji" dla eLektronu (raz).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = InstallState.NeedsPermission(version)
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        _state.value = InstallState.ReadyToInstall(version)
        // Z tła Android nie otworzy okna - wtedy zostaje przycisk "Zainstaluj" w aplikacji.
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Nie udało się otworzyć instalatora", it) }
    }

    override fun resumeIfPermitted() {
        val st = _state.value
        if (st is InstallState.NeedsPermission &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls())
        ) install()
    }

    private companion object {
        const val TAG = "ApkUpdateInstaller"
    }
}
