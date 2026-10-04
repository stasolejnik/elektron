package pl.zse.bydgoszcz.elektron

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import pl.zse.bydgoszcz.elektron.crash.CrashReporter
import pl.zse.bydgoszcz.elektron.work.PushTopics
import pl.zse.bydgoszcz.elektron.work.SyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class ElektronApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var pushTopics: PushTopics


    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Jako pierwsze: raport awarii ma powstać także wtedy, gdy wysypie się reszta onCreate.
        CrashReporter.install(this)
        // Tylko harmonogram cykliczny. Każdą synchronizację (WorkManager co 15 min, wybór klasy,
        // odświeżanie na ekranach) wykonuje SyncCoordinator. Flaga "trwa synchronizacja" żyje
        // w jego pamięci - nowy proces zaczyna od false, więc nie ma już czego zerować
        // (dawne zerowanie flag w bazie potrafiło zgasić pracę workera, który uruchomił proces).
        // Nowa wersja na GitHubie (najwyżej co 12 h; wynik trafia do banera na stronie głównej).
        // Sprawdzanie aktualizacji przy wejściu do aplikacji (MainActivity.onStart).
        syncScheduler.ensurePeriodic()
        // Funkcja #7: subskrypcja tematów FCM (push bez backendu appki).
        pushTopics.start()  // gms: tematy FCM; foss (F-Droid): nic
    }
}
