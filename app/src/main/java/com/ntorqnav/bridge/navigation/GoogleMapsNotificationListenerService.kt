package com.ntorqnav.bridge.navigation

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ntorqnav.bridge.logging.AppLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class GoogleMapsNotificationListenerService : NotificationListenerService() {

    companion object {
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

        private val _rawNotificationFlow = MutableSharedFlow<NotificationPayload>(replay = 1)
        val rawNotificationFlow = _rawNotificationFlow.asSharedFlow()

        @Volatile
        var isConnected: Boolean = false
            private set
    }

    data class NotificationPayload(
        val title: String?,
        val text: String?,
        val subText: String?,
        val postTime: Long
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        AppLogger.maps("NotificationListener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        AppLogger.maps("NotificationListener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (sbn.packageName != GOOGLE_MAPS_PACKAGE) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        AppLogger.maps("Google Maps notification: title='$title', text='$text', subText='$subText'")
        _rawNotificationFlow.tryEmit(
            NotificationPayload(
                title = title,
                text = text,
                subText = subText,
                postTime = sbn.postTime
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.packageName == GOOGLE_MAPS_PACKAGE) {
            AppLogger.maps("Google Maps navigation notification removed (navigation stopped)")
        }
    }
}
