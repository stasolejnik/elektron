package pl.zse.bydgoszcz.elektron.domain.model

import java.time.Instant

data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val deepLink: String?,
    val createdAt: Instant,
    val readAt: Instant?
)

enum class NotificationType {
    SUBSTITUTION,
    NEW_ANNOUNCEMENT,
    OTHER
}
