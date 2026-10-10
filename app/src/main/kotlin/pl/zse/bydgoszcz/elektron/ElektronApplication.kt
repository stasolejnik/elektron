package pl.zse.bydgoszcz.elektron

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import pl.zse.bydgoszcz.elektron.crash.CrashReporter
import pl.zse.bydgoszcz.elektron.work.PushTopics
import pl.zse.bydgoszcz.elektron.work.SyncScheduler
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class ElektronApplication : Application(), Configuration.Provider, coil.ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var pushTopics: PushTopics
    @Inject lateinit var widgetUpdater: pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
    @Inject lateinit var transitPreferences: pl.zse.bydgoszcz.elektron.data.repository.TransitPreferencesRepository
    private var nightMode = -1


    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        // Jako pierwsze, jeszcze przed super.onCreate(): tam Hilt wstrzykuje zależności
        // (WorkManager, harmonogram, w wersji z GitHuba Firebase) - awaria przy ich tworzeniu
        // też ma dać raport. Kontekst aplikacji jest już dostępny.
        CrashReporter.install(this)
        // Symulowany czas z trybu dewelopera - zanim cokolwiek (np. widżet) zapyta o godzinę.
        val devClock = getSharedPreferences("dev_clock", MODE_PRIVATE)
        pl.zse.bydgoszcz.elektron.domain.util.AppClock.restore(devClock.getLong("offset_ms", 0L))
        pl.zse.bydgoszcz.elektron.domain.util.AppClock.persist = { devClock.edit().putLong("offset_ms", it).apply() }
        super.onCreate()
        // Tylko harmonogram cykliczny. Każdą synchronizację (WorkManager co 15 min, wybór klasy,
        // odświeżanie na ekranach) wykonuje SyncCoordinator. Flaga "trwa synchronizacja" żyje
        // w jego pamięci - nowy proces zaczyna od false, więc nie ma już czego zerować
        // (dawne zerowanie flag w bazie potrafiło zgasić pracę workera, który uruchomił proces).
        // Nowa wersja na GitHubie (najwyżej co 12 h; wynik trafia do banera na stronie głównej).
        // Sprawdzanie aktualizacji przy wejściu do aplikacji (ElektronActivity.onStart).
        syncScheduler.ensurePeriodic()
        // Funkcja #7: subskrypcja tematów FCM (push bez backendu appki).
        pushTopics.start()  // gms: tematy FCM; foss (F-Droid): nic
        nightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        // Zmiana symulowanego czasu (tryb dewelopera) - widżety od razu w nowym czasie.
        kotlinx.coroutines.MainScope().launch {
            pl.zse.bydgoszcz.elektron.domain.util.AppClock.offsetMs.drop(1).collect { runCatching { widgetUpdater.requestUpdate() } }
        }
        // Przezroczyste widżety biorą kolory treści z jasności tapety - po zmianie tapety od razu
        // (gdy proces żyje; inaczej przy najbliższym odświeżeniu widżetów).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) runCatching {
            // Tylko gdy zmienia się wybór ciemny/jasny tekst - tapety animowane zgłaszają kolory często.
            // Kolory podaje system w słuchaczu (bez odczytu tapety w wątku głównym przy starcie).
            var darkText: Boolean? = null
            var known = false
            android.app.WallpaperManager.getInstance(this).addOnColorsChangedListener({ colors, which ->
                if ((which and android.app.WallpaperManager.FLAG_SYSTEM) != 0) {
                    val now = pl.zse.bydgoszcz.elektron.widget.WidgetWallpaper.supportsDarkText(colors)
                    if (!known || now != darkText) { known = true; darkText = now; widgetUpdater.requestUpdate() }
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        // Skróty po przytrzymaniu ikony: Odjazdy tylko z włączoną zakładką.
        kotlinx.coroutines.MainScope().launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { pl.zse.bydgoszcz.elektron.launcher.AppIcons.migrate(this@ElektronApplication) }
            }
            // Błąd odczytu ustawień nie może zamknąć procesu (start także w tle: WorkManager, push).
            transitPreferences.preferences.map { it.visible }.distinctUntilChanged()
                .catch { android.util.Log.w("ElektronApp", "Skróty: nie udało się odczytać ustawień Odjazdów", it) }
                .collect { visible ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { pl.zse.bydgoszcz.elektron.launcher.AppShortcuts.update(this@ElektronApplication, visible) }
                    }
                }
        }
    }

    /**
     * Zdjęcia ogłoszeń: pamięć podręczna na dysku najwyżej 50 MB (domyślnie Coil brał do 2% wolnego
     * miejsca, do 250 MB) - po przekroczeniu znikają najdawniej oglądane. Katalog cache aplikacji,
     * więc system może go też wyczyścić przy braku miejsca.
     */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(IMAGE_CACHE_BYTES).build() }
        .build()

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Przełączenie trybu ciemnego: widżety od razu w nowych kolorach. Android 8-11 wybiera kolory
        // motywu systemowego przy rysowaniu, a od 12 robi to launcher - ale gotowych wierszy listy
        // Planu dnia nie przerysowywał. Dawniej zostawały w starych kolorach do następnego
        // odświeżenia, nawet do północy. Działa, gdy proces aplikacji żyje.
        val night = newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (nightMode != -1 && night != nightMode) {
            widgetUpdater.requestUpdate()
        }
        nightMode = night
    }

    private companion object {
        const val IMAGE_CACHE_BYTES = 50L * 1024 * 1024
    }
}
