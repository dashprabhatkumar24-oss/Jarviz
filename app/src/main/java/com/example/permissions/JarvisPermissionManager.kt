package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.notifications.NotificationCache

data class PermissionItemInfo(
    val id: String,
    val title: String,
    val androidPermission: String?,
    val whyNeeded: String,
    val isGranted: Boolean,
    val isSpecialAccess: Boolean = false,
    val settingsAction: String? = null
)

class JarvisPermissionManager(private val context: Context) {

    fun isPermissionGranted(permission: String): Boolean {
        if (permission == Manifest.permission.BLUETOOTH_CONNECT && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        if (permission == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getMissingPermissions(required: List<String>): List<String> {
        return required.filterNot { isPermissionGranted(it) }
    }

    fun getAllPermissionStatuses(): List<PermissionItemInfo> {
        val list = mutableListOf(
            PermissionItemInfo(
                id = "microphone",
                title = "Microphone (Voice Input)",
                androidPermission = Manifest.permission.RECORD_AUDIO,
                whyNeeded = "Required to hear your voice commands, wake word ('Hey Jarvis'), and speech-to-text queries.",
                isGranted = isPermissionGranted(Manifest.permission.RECORD_AUDIO)
            ),
            PermissionItemInfo(
                id = "contacts",
                title = "Contacts Lookup",
                androidPermission = Manifest.permission.READ_CONTACTS,
                whyNeeded = "Required to resolve contact names like 'Mom' or 'John' into phone numbers when calling or messaging.",
                isGranted = isPermissionGranted(Manifest.permission.READ_CONTACTS)
            ),
            PermissionItemInfo(
                id = "phone",
                title = "Phone Calls",
                androidPermission = Manifest.permission.CALL_PHONE,
                whyNeeded = "Required to place direct phone calls after your confirmation. Without this, JARVIS opens the dialer instead.",
                isGranted = isPermissionGranted(Manifest.permission.CALL_PHONE)
            ),
            PermissionItemInfo(
                id = "sms",
                title = "SMS Messaging",
                androidPermission = Manifest.permission.SEND_SMS,
                whyNeeded = "Allows sending SMS messages after explicit confirmation, or opening your default SMS app pre-filled.",
                isGranted = isPermissionGranted(Manifest.permission.SEND_SMS)
            ),
            PermissionItemInfo(
                id = "calendar",
                title = "Calendar Events & Schedule",
                androidPermission = Manifest.permission.READ_CALENDAR,
                whyNeeded = "Required to read your upcoming appointments today and schedule new calendar meetings.",
                isGranted = isPermissionGranted(Manifest.permission.READ_CALENDAR)
            ),
            PermissionItemInfo(
                id = "location",
                title = "Location & Navigation",
                androidPermission = Manifest.permission.ACCESS_FINE_LOCATION,
                whyNeeded = "Used for local weather context and finding nearby places such as petrol stations or restaurants.",
                isGranted = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
            ),
            PermissionItemInfo(
                id = "bluetooth",
                title = "Bluetooth Control",
                androidPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null,
                whyNeeded = "Required on Android 12+ to inspect connected Bluetooth audio devices and open Bluetooth controls.",
                isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
                } else true
            ),
            PermissionItemInfo(
                id = "post_notifications",
                title = "Foreground Status Notification",
                androidPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
                whyNeeded = "Displays a clear privacy indicator notification whenever background voice listening is enabled.",
                isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
                } else true
            ),
            PermissionItemInfo(
                id = "notification_listener",
                title = "Notification Reader Access",
                androidPermission = null,
                whyNeeded = "Required only when you ask JARVIS to 'Read my notifications' aloud. Never accessed without your request.",
                isGranted = NotificationCache.isNotificationAccessEnabled(context),
                isSpecialAccess = true,
                settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            ),
            PermissionItemInfo(
                id = "write_settings",
                title = "Modify System Settings (Brightness)",
                androidPermission = null,
                whyNeeded = "Optional special access to adjust screen brightness directly. Otherwise JARVIS opens Display Settings.",
                isGranted = Settings.System.canWrite(context),
                isSpecialAccess = true,
                settingsAction = Settings.ACTION_MANAGE_WRITE_SETTINGS
            )
        )
        return list
    }

    fun openAppSystemSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openSpecialSettings(action: String) {
        try {
            val intent = Intent(action).apply {
                if (action == Settings.ACTION_MANAGE_WRITE_SETTINGS) {
                    data = Uri.parse("package:${context.packageName}")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSystemSettings()
        }
    }
}
