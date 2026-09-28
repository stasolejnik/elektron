package pl.zse.bydgoszcz.elektron.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.zse.bydgoszcz.elektron.BuildConfig
import pl.zse.bydgoszcz.elektron.data.local.SyncStateDao
import pl.zse.bydgoszcz.elektron.data.local.SyncStateEntity
import pl.zse.bydgoszcz.elektron.domain.model.AppVersion
import pl.zse.bydgoszcz.elektron.domain.repository.AppUpdate
import pl.zse.bydgoszcz.elektron.domain.repository.UpdateRepository
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprawdzanie nowej wersji w GitHub Releases. Stan (kiedy sprawdzono, najnowsza wersja,
 * zamknięty baner) w tabeli sync_state — bez migracji bazy.
 *
 * Zapytanie idzie przez wspólny klient OkHttp: cache HTTP z rewalidacją (ETag), więc
 * odpowiedź "bez zmian" (304) nie liczy się do limitu GitHuba (60 zapytań/h bez logowania).
 */
@Singleton
class UpdateRepositoryImpl @Inject constructor(
    private val client: OkHttpClient,
    private val syncStateDao: SyncStateDao
) : UpdateRepository {

    private val currentVersion = BuildConfig.VERSION_NAME

    override val availableUpdate: Flow<AppUpdate?> = combine(
        syncStateDao.observe(KEY_LATEST),
        syncStateDao.observe(KEY_DISMISSED)
    ) { latest, dismissed ->
        val update = decode(latest?.message) ?: return@combine null
        when {
            !AppVersion.isNewer(update.versionName, currentVersion) -> null
            dismissed?.message == update.versionName -> null
            else -> update
        }
    }

    override suspend fun check(force: Boolean): Result<AppUpdate?> = withContext(Dispatchers.IO) {
        // Wersja F-Droid (foss): bez sprawdzania GitHuba — F-Droid sam aktualizuje aplikację
        // i podpisuje ją własnym kluczem (APK z GitHuba i tak by się nie zainstalował).
        if (!BuildConfig.UPDATE_CHECK) return@withContext Result.success(null)
        runCatching {
            val now = Instant.now().epochSecond
            if (!force) {
                val last = syncStateDao.get(KEY_CHECKED)?.lastSyncEpochSeconds ?: 0L
                if (now - last < CHECK_INTERVAL_SECONDS) {
                    return@runCatching decode(syncStateDao.get(KEY_LATEST)?.message)
                        ?.takeIf { AppVersion.isNewer(it.versionName, currentVersion) }
                }
            }
            val request = Request.Builder().url(RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .get().build()
            val releases = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("GitHub: HTTP ${resp.code}")
                GitHubReleases.parse(resp.body?.string() ?: throw IOException("GitHub: pusta odpowiedź"))
            }
            val newest = releases.filterNot { it.draft }
                .maxWithOrNull { a, b -> AppVersion.compare(a.tag, b.tag) }
                ?.let { AppUpdate(it.tag.removePrefix("v").removePrefix("V"), it.pageUrl) }
            syncStateDao.upsert(SyncStateEntity(KEY_CHECKED, now, "ok", null))
            if (newest != null) syncStateDao.upsert(SyncStateEntity(KEY_LATEST, now, "ok", encode(newest)))
            newest?.takeIf { AppVersion.isNewer(it.versionName, currentVersion) }
        }
    }

    override suspend fun dismiss(versionName: String) = withContext(Dispatchers.IO) {
        syncStateDao.upsert(SyncStateEntity(KEY_DISMISSED, Instant.now().epochSecond, "ok", versionName))
    }

    private fun encode(u: AppUpdate) = "${u.versionName}\n${u.pageUrl}"

    private fun decode(raw: String?): AppUpdate? {
        val parts = raw?.split('\n') ?: return null
        if (parts.size < 2 || parts[0].isBlank()) return null
        return AppUpdate(parts[0], parts[1])
    }

    private companion object {
        const val RELEASES_API = "https://api.github.com/repos/stasolejnik/elektron/releases?per_page=10"
        const val CHECK_INTERVAL_SECONDS = 12 * 60 * 60L
        const val KEY_CHECKED = "update_checked"
        const val KEY_LATEST = "update_latest"
        const val KEY_DISMISSED = "update_dismissed"
    }
}
