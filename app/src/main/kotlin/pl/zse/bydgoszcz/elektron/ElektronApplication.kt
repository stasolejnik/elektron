package pl.zse.bydgoszcz.elektron

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.work.FcmTopicManager
import pl.zse.bydgoszcz.elektron.work.SyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class ElektronApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var fcmTopicManager: FcmTopicManager
    @Inject lateinit var notificationsRepo: NotificationsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Tylko harmonogram cykliczny. Sync "na żądanie" odpala:
        //  - ClassSelection po wyborze klasy (Setup i Ustawienia),
        //  - pull-to-refresh na Starcie, w Planie, Zastępstwach i Ogłoszeniach,
        //  - WorkManager co 15 min, push FCM na bieżąco.
        // Flagi "trwa sync" są w bazie. Jeśli proces został zabity w trakcie syncu, zostawały
        // na "true" — Start pokazywał "Synchronizacja…", a ekran grup kręcił się bez końca.
        // Przy starcie procesu żaden sync jeszcze nie trwa, więc zerujemy je.
        appScope.launch {
            notificationsRepo.setIsSyncing(false)
            notificationsRepo.setInitialSyncPending(false)
        }
        syncScheduler.ensurePeriodic()
        // Funkcja #7: subskrypcja tematów FCM (push bez backendu appki).
        fcmTopicManager.start()
    }
}
