package pl.zse.bydgoszcz.elektron.work

import kotlinx.coroutines.flow.Flow
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.Substitution

/**
 * Abstrakcja nad źródłem powiadomień push. Na razie pusta — powiadomienia
 * generujemy lokalnie w SyncWorker. Gdyby doszedł FCM: dopisz FcmPushSource.
 */
interface PushSource {
    fun observeEvents(): Flow<PushEvent>
    suspend fun subscribe()
    suspend fun unsubscribe()
}

sealed class PushEvent {
    data class NewSubstitution(val substitution: Substitution) : PushEvent()
    data class NewAnnouncement(val announcement: Announcement) : PushEvent()
}
