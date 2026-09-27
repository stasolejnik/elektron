package pl.zse.bydgoszcz.elektron.work

import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.Substitution

interface NotificationSink {
    suspend fun postSubstitution(sub: Substitution, originalSubject: String? = null)
    suspend fun postAnnouncement(ann: Announcement)
    suspend fun postGeneric(title: String, body: String, deepLink: String?)
}
