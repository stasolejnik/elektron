package pl.zse.bydgoszcz.elektron.domain.repository

import kotlinx.coroutines.flow.Flow

/** Nowa wersja aplikacji dostępna na GitHubie. [pageUrl] — strona wydania z plikiem APK. */
data class AppUpdate(val versionName: String, val pageUrl: String)

interface UpdateRepository {
    /** Nowsza wersja niż zainstalowana (i niezamknięta przez użytkownika), inaczej null. */
    val availableUpdate: Flow<AppUpdate?>

    /**
     * Sprawdza GitHub Releases. Bez [force] najwyżej co kilka godzin.
     * Wynik: nowsza wersja albo null (masz najnowszą).
     */
    suspend fun check(force: Boolean = false): Result<AppUpdate?>

    /** "Później" — nie pokazuj banera dla tej wersji. */
    suspend fun dismiss(versionName: String)
}
