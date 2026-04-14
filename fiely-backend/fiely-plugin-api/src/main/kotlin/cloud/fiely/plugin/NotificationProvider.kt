package cloud.fiely.plugin

import org.pf4j.ExtensionPoint

/**
 * Extension point for pluggable notification delivery.
 *
 * Fiely core provides in-app notifications by default. Additional channels
 * (email, push, webhook) ship as plugins.
 */
interface NotificationProvider : ExtensionPoint {
    val id: String
    val displayName: String

    fun send(notification: Notification): Boolean
    fun supports(channel: NotificationChannel): Boolean
}

enum class NotificationChannel {
    EMAIL,
    PUSH,
    WEBHOOK,
    IN_APP
}

data class Notification(
    val channel: NotificationChannel,
    val recipientId: String,
    val title: String,
    val body: String,
    val metadata: Map<String, String> = emptyMap()
)
