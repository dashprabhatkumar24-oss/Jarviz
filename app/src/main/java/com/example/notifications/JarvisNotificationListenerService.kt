package com.example.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long
)

object NotificationCache {
    private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()

    fun addOrUpdate(item: CapturedNotification) {
        val current = _notifications.value.toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        _notifications.value = current.take(25)
    }

    fun remove(id: String) {
        _notifications.value = _notifications.value.filterNot { it.id == id }
    }

    fun clear() {
        _notifications.value = emptyList()
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val cn = ComponentName(context, JarvisNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }
}

class JarvisNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { parseAndCache(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let { NotificationCache.remove(it.key) }
    }

    private fun refreshActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            for (sbn in active) {
                parseAndCache(sbn)
            }
        } catch (_: Exception) {
            // Security or binder state check
        }
    }

    private fun parseAndCache(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return // Skip JARVIS own foreground notification
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        val appName = try {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            sbn.packageName.substringAfterLast('.')
        }

        NotificationCache.addOrUpdate(
            CapturedNotification(
                id = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                text = text,
                postTime = sbn.postTime
            )
        )
    }
}
