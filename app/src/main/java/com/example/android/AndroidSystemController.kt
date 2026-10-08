package com.example.android

import android.Manifest
import android.app.ActivityManager
import android.app.SearchManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import com.example.commands.ActionCategory
import com.example.commands.CommandExecutionResult
import com.example.commands.JarvisCommandAction
import com.example.database.JarvisRepository
import com.example.notifications.NotificationCache
import com.example.permissions.JarvisPermissionManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

data class DeviceTelemetry(
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val isNetworkOnline: Boolean,
    val networkType: String,
    val availableRamMb: Long,
    val totalRamMb: Long,
    val mediaVolumePercent: Int,
    val androidVersion: String,
    val deviceModel: String
)

class AndroidSystemController(
    private val context: Context,
    private val permissionManager: JarvisPermissionManager,
    private val repository: JarvisRepository
) {

    fun getDeviceTelemetry(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 85

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netType = when {
            capabilities == null -> "Offline"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Connected"
        }

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        val totalMb = memInfo.totalMem / (1024 * 1024)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7
        val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
        val volPct = (currentVol * 100) / maxVol

        return DeviceTelemetry(
            batteryPercentage = batteryPct,
            isCharging = isCharging,
            isNetworkOnline = isOnline,
            networkType = netType,
            availableRamMb = availMb,
            totalRamMb = totalMb,
            mediaVolumePercent = volPct,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        )
    }

    fun discoverInstalledApps(): List<InstalledAppInfo> {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            resolveInfos.mapNotNull { info ->
                val label = info.loadLabel(pm)?.toString()?.trim()
                val pkg = info.activityInfo?.packageName
                if (!label.isNullOrEmpty() && !pkg.isNullOrEmpty()) {
                    InstalledAppInfo(label, pkg)
                } else null
            }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun executeAction(
        action: JarvisCommandAction,
        userConfirmed: Boolean = false,
        requireConfirmationPolicy: Boolean = true
    ): CommandExecutionResult {
        // 1. Check confirmation gate for sensitive actions
        if (requireConfirmationPolicy && action.requiresConfirmation && !userConfirmed) {
            val prompt = action.confirmationPrompt
                ?: buildDefaultConfirmationPrompt(action)
            return CommandExecutionResult.RequiresConfirmation(
                pendingAction = action,
                reason = prompt
            )
        }

        // 2. Dispatch to specialized handler
        return when (action.category) {
            ActionCategory.CALL_CONTACT -> handlePhoneCall(action)
            ActionCategory.SEND_SMS -> handleSendSms(action)
            ActionCategory.SEND_MESSAGE -> handleSendAppMessage(action)
            ActionCategory.OPEN_APP -> handleOpenApp(action)
            ActionCategory.SEARCH_WEB -> handleSearchWeb(action)
            ActionCategory.CREATE_ALARM -> handleCreateAlarm(action)
            ActionCategory.CREATE_TIMER -> handleCreateTimer(action)
            ActionCategory.CREATE_REMINDER -> handleCreateReminder(action)
            ActionCategory.CREATECALENDAREVENT -> handleCalendarEvent(action)
            ActionCategory.CONTROL_VOLUME -> handleControlVolume(action)
            ActionCategory.CONTROL_BRIGHTNESS -> handleControlBrightness(action)
            ActionCategory.OPEN_SETTINGS -> handleOpenSettings(action)
            ActionCategory.PLAY_MEDIA -> handleMediaControl(action, isPlay = true)
            ActionCategory.PAUSE_MEDIA -> handleMediaControl(action, isPlay = false)
            ActionCategory.NAVIGATE -> handleNavigate(action)
            ActionCategory.READ_NOTIFICATION -> handleReadNotifications(action)
            ActionCategory.DEVICE_INFORMATION -> handleDeviceInfo(action)
            ActionCategory.WEATHER -> handleWeather(action)
            ActionCategory.SWITCH_LANGUAGE -> CommandExecutionResult.Success(
                spokenMessage = action.spokenResponse,
                detailMessage = "Active language switched to ${action.parameters["language"] ?: action.detectedLanguage}",
                actionCategory = ActionCategory.SWITCH_LANGUAGE
            )
            ActionCategory.GENERALAIQUERY -> CommandExecutionResult.Success(
                spokenMessage = action.spokenResponse,
                detailMessage = null,
                actionCategory = ActionCategory.GENERALAIQUERY
            )
        }
    }

    private fun buildDefaultConfirmationPrompt(action: JarvisCommandAction): String {
        return when (action.category) {
            ActionCategory.CALL_CONTACT -> {
                val target = action.parameters["contact_name"] ?: action.parameters["phone_number"] ?: "this contact"
                "Please confirm: Do you want me to place a call to $target?"
            }
            ActionCategory.SEND_SMS -> {
                val target = action.parameters["contact_name"] ?: action.parameters["phone_number"] ?: "recipient"
                val msg = action.parameters["message"] ?: ""
                "Please confirm sending SMS to $target: \"$msg\""
            }
            ActionCategory.SEND_MESSAGE -> {
                val target = action.parameters["contact_name"] ?: "recipient"
                val app = action.parameters["app"] ?: "WhatsApp"
                val msg = action.parameters["message"] ?: ""
                "Please confirm preparing $app message for $target: \"$msg\""
            }
            else -> "Please confirm executing ${action.category.title}."
        }
    }

    private fun lookupContactPhoneNumber(nameQuery: String): Pair<String, String>? {
        if (!permissionManager.isPermissionGranted(Manifest.permission.READ_CONTACTS)) {
            return null
        }
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$nameQuery%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIndex >= 0 && numIndex >= 0) {
                    val foundName = it.getString(nameIndex) ?: nameQuery
                    val foundNumber = it.getString(numIndex) ?: ""
                    if (foundNumber.isNotBlank()) {
                        return Pair(foundName, foundNumber)
                    }
                }
            }
        }
        return null
    }

    private fun handlePhoneCall(action: JarvisCommandAction): CommandExecutionResult {
        val rawContact = action.parameters["contact_name"]?.trim().orEmpty()
        val rawNumber = action.parameters["phone_number"]?.trim().orEmpty()

        var resolvedName = rawContact.ifEmpty { rawNumber }
        var phoneNumber = rawNumber

        if (phoneNumber.isEmpty() && rawContact.isNotEmpty()) {
            // If contact looks like digits, use directly
            if (rawContact.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) {
                phoneNumber = rawContact
            } else {
                if (!permissionManager.isPermissionGranted(Manifest.permission.READ_CONTACTS)) {
                    return CommandExecutionResult.PermissionRequired(
                        missingPermissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE),
                        pendingAction = action,
                        explanation = "I need Contacts and Phone permissions to look up $rawContact's phone number and place the call."
                    )
                }
                val match = lookupContactPhoneNumber(rawContact)
                if (match != null) {
                    resolvedName = match.first
                    phoneNumber = match.second
                }
            }
        }

        return try {
            if (phoneNumber.isNotEmpty() && permissionManager.isPermissionGranted(Manifest.permission.CALL_PHONE)) {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                CommandExecutionResult.Success(
                    spokenMessage = "Calling $resolvedName now.",
                    detailMessage = "Dialing $phoneNumber",
                    actionCategory = ActionCategory.CALL_CONTACT
                )
            } else {
                // Open dialer safely if number unknown or CALL_PHONE not granted
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    if (phoneNumber.isNotEmpty()) {
                        data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                val msg = if (phoneNumber.isNotEmpty()) {
                    "I've opened the phone dialer with $resolvedName's number ready."
                } else {
                    "I couldn't find '$rawContact' in your contacts, so I've opened the phone dialer for you."
                }
                CommandExecutionResult.Success(
                    spokenMessage = msg,
                    detailMessage = "Opened Phone Dialer",
                    actionCategory = ActionCategory.CALL_CONTACT
                )
            }
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Unable to launch phone dialer on this device.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.CALL_CONTACT
            )
        }
    }

    private fun handleSendSms(action: JarvisCommandAction): CommandExecutionResult {
        val rawContact = action.parameters["contact_name"]?.trim().orEmpty()
        val rawNumber = action.parameters["phone_number"]?.trim().orEmpty()
        val messageBody = action.parameters["message"]?.trim().orEmpty()

        var resolvedName = rawContact.ifEmpty { rawNumber.ifEmpty { "recipient" } }
        var phoneNumber = rawNumber

        if (phoneNumber.isEmpty() && rawContact.isNotEmpty()) {
            if (rawContact.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) {
                phoneNumber = rawContact
            } else if (permissionManager.isPermissionGranted(Manifest.permission.READ_CONTACTS)) {
                val match = lookupContactPhoneNumber(rawContact)
                if (match != null) {
                    resolvedName = match.first
                    phoneNumber = match.second
                }
            } else {
                return CommandExecutionResult.PermissionRequired(
                    missingPermissions = listOf(Manifest.permission.READ_CONTACTS),
                    pendingAction = action,
                    explanation = "I need Contacts permission to find $rawContact's phone number for your SMS."
                )
            }
        }

        return try {
            if (phoneNumber.isNotEmpty() && messageBody.isNotEmpty() &&
                permissionManager.isPermissionGranted(Manifest.permission.SEND_SMS)
            ) {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager?.sendTextMessage(phoneNumber, null, messageBody, null, null)
                CommandExecutionResult.Success(
                    spokenMessage = "SMS sent to $resolvedName.",
                    detailMessage = "Sent to $phoneNumber: \"$messageBody\"",
                    actionCategory = ActionCategory.SEND_SMS
                )
            } else {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:${Uri.encode(phoneNumber)}")
                    putExtra("sms_body", messageBody)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(smsIntent)
                CommandExecutionResult.Success(
                    spokenMessage = "I've prepared your SMS for $resolvedName in the messaging app.",
                    detailMessage = "Prepared SMS: \"$messageBody\"",
                    actionCategory = ActionCategory.SEND_SMS
                )
            }
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Could not open the SMS messaging application.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.SEND_SMS
            )
        }
    }

    private fun handleSendAppMessage(action: JarvisCommandAction): CommandExecutionResult {
        val contactName = action.parameters["contact_name"]?.trim().orEmpty()
        val messageBody = action.parameters["message"]?.trim().orEmpty()
        val appName = action.parameters["app"]?.trim()?.ifEmpty { "WhatsApp" } ?: "WhatsApp"

        var phoneNumber = action.parameters["phone_number"]?.trim().orEmpty()
        if (phoneNumber.isEmpty() && contactName.isNotEmpty() &&
            permissionManager.isPermissionGranted(Manifest.permission.READ_CONTACTS)
        ) {
            phoneNumber = lookupContactPhoneNumber(contactName)?.second.orEmpty()
        }

        return try {
            if (appName.contains("whatsapp", ignoreCase = true)) {
                val cleanDigits = phoneNumber.filter { it.isDigit() }
                val uri = if (cleanDigits.isNotEmpty()) {
                    Uri.parse("https://api.whatsapp.com/send?phone=$cleanDigits&text=${Uri.encode(messageBody)}")
                } else {
                    Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(messageBody)}")
                }
                val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(waIntent)
                CommandExecutionResult.Success(
                    spokenMessage = "Sure. I've prepared the WhatsApp message for ${contactName.ifEmpty { "your contact" }}.",
                    detailMessage = "WhatsApp: \"$messageBody\"",
                    actionCategory = ActionCategory.SEND_MESSAGE
                )
            } else {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageBody)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "Send message via").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                CommandExecutionResult.Success(
                    spokenMessage = "I've prepared your message for ${contactName.ifEmpty { "sharing" }}.",
                    detailMessage = "Message: \"$messageBody\"",
                    actionCategory = ActionCategory.SEND_MESSAGE
                )
            }
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Could not open $appName. Please verify it is installed.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.SEND_MESSAGE
            )
        }
    }

    private fun handleOpenApp(action: JarvisCommandAction): CommandExecutionResult {
        val targetApp = action.parameters["app_name"]?.trim().orEmpty()
        val lower = targetApp.lowercase()

        return try {
            // Check built-in system intents first for common categories
            when {
                lower.contains("camera") -> {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return CommandExecutionResult.Success(
                        spokenMessage = "Opening Camera.",
                        detailMessage = "Launched Camera Intent",
                        actionCategory = ActionCategory.OPEN_APP
                    )
                }
                lower.contains("gallery") || lower.contains("photos") -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        type = "image/*"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return CommandExecutionResult.Success(
                        spokenMessage = "Opening Gallery.",
                        detailMessage = "Launched Photo Viewer",
                        actionCategory = ActionCategory.OPEN_APP
                    )
                }
                lower.contains("setting") -> {
                    return handleOpenSettings(action)
                }
                lower.contains("contact") -> {
                    val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return CommandExecutionResult.Success(
                        spokenMessage = "Opening Contacts.",
                        detailMessage = "Launched Contacts",
                        actionCategory = ActionCategory.OPEN_APP
                    )
                }
                lower.contains("dialer") || lower.contains("phone") -> {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return CommandExecutionResult.Success(
                        spokenMessage = "Opening Phone Dialer.",
                        detailMessage = "Launched Dialer",
                        actionCategory = ActionCategory.OPEN_APP
                    )
                }
            }

            // Discover installed apps by label or package name
            val installed = discoverInstalledApps()
            val match = installed.firstOrNull {
                it.appName.equals(targetApp, ignoreCase = true) ||
                    it.appName.contains(targetApp, ignoreCase = true) ||
                    targetApp.contains(it.appName, ignoreCase = true) ||
                    it.packageName.contains(lower)
            }

            if (match != null) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(match.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return CommandExecutionResult.Success(
                        spokenMessage = "${match.appName} is open.",
                        detailMessage = "Package: ${match.packageName}",
                        actionCategory = ActionCategory.OPEN_APP
                    )
                }
            }

            // Known web/deep-link fallback for popular apps in emulator environments
            val webFallbacks = mapOf(
                "youtube" to "https://m.youtube.com",
                "whatsapp" to "https://web.whatsapp.com",
                "spotify" to "https://open.spotify.com",
                "chrome" to "https://www.google.com",
                "maps" to "https://maps.google.com",
                "gmail" to "https://mail.google.com"
            )
            val matchedFallback = webFallbacks.entries.firstOrNull { lower.contains(it.key) }
            if (matchedFallback != null) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(matchedFallback.value)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                return CommandExecutionResult.Success(
                    spokenMessage = "Opening $targetApp.",
                    detailMessage = "Opened ${matchedFallback.value}",
                    actionCategory = ActionCategory.OPEN_APP
                )
            }

            CommandExecutionResult.Error(
                userFriendlyMessage = "I couldn't find '$targetApp' installed on your device.",
                technicalDetail = "Checked ${installed.size} launchable packages.",
                actionCategory = ActionCategory.OPEN_APP
            )
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Unable to launch $targetApp right now.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.OPEN_APP
            )
        }
    }

    private fun handleSearchWeb(action: JarvisCommandAction): CommandExecutionResult {
        val query = action.parameters["query"]?.trim().orEmpty()
        val engine = action.parameters["engine"]?.lowercase().orEmpty()

        if (query.isEmpty()) {
            return CommandExecutionResult.Error(
                userFriendlyMessage = "Please specify what you would like me to search for.",
                actionCategory = ActionCategory.SEARCH_WEB
            )
        }

        return try {
            if (engine.contains("youtube")) {
                val ytIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(ytIntent)
                CommandExecutionResult.Success(
                    spokenMessage = "Searching YouTube for $query.",
                    detailMessage = "YouTube query: $query",
                    actionCategory = ActionCategory.SEARCH_WEB
                )
            } else {
                val searchIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(searchIntent)
                CommandExecutionResult.Success(
                    spokenMessage = "Searching Google for $query.",
                    detailMessage = "Web search: $query",
                    actionCategory = ActionCategory.SEARCH_WEB
                )
            }
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Unable to open a web browser for your search.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.SEARCH_WEB
            )
        }
    }

    private fun handleCreateAlarm(action: JarvisCommandAction): CommandExecutionResult {
        val hour = action.parameters["hour"]?.toIntOrNull() ?: 7
        val minute = action.parameters["minute"]?.toIntOrNull() ?: 0
        val label = action.parameters["label"] ?: "JARVIS Alarm"

        val formattedTime = String.format(Locale.US, "%02d:%02d", hour, minute)

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandExecutionResult.Success(
                spokenMessage = "Done. The alarm is set for $formattedTime.",
                detailMessage = "Alarm '$label' scheduled at $formattedTime",
                actionCategory = ActionCategory.CREATE_ALARM
            )
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "I couldn't access the system Clock app to set the alarm for $formattedTime.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.CREATE_ALARM
            )
        }
    }

    private fun handleCreateTimer(action: JarvisCommandAction): CommandExecutionResult {
        val mode = action.parameters["mode"]?.lowercase() ?: "timer"
        val seconds = action.parameters["seconds"]?.toIntOrNull() ?: 60
        val label = action.parameters["label"] ?: "JARVIS Timer"

        return try {
            if (mode.contains("stopwatch")) {
                val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(clockIntent)
                CommandExecutionResult.Success(
                    spokenMessage = "Opening the Clock app for your stopwatch.",
                    detailMessage = "Stopwatch opened",
                    actionCategory = ActionCategory.CREATE_TIMER
                )
            } else {
                val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(timerIntent)
                val mins = seconds / 60
                val remSec = seconds % 60
                val durationText = if (mins > 0) "$mins minute${if (mins > 1) "s" else ""}" else "$remSec seconds"
                CommandExecutionResult.Success(
                    spokenMessage = "Timer set for $durationText.",
                    detailMessage = "Timer '$label' ($seconds sec)",
                    actionCategory = ActionCategory.CREATE_TIMER
                )
            }
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Unable to start a system timer on this device.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.CREATE_TIMER
            )
        }
    }

    private suspend fun handleCreateReminder(action: JarvisCommandAction): CommandExecutionResult {
        val title = action.parameters["title"]?.trim()?.ifEmpty { "General Reminder" } ?: "General Reminder"
        val date = action.parameters["date"]?.trim()?.ifEmpty { "Today" } ?: "Today"
        val time = action.parameters["time"]?.trim()?.ifEmpty { "09:00" } ?: "09:00"

        repository.addReminder(title = title, date = date, time = time)
        return CommandExecutionResult.Success(
            spokenMessage = "Done. I'll remind you to $title ($date at $time).",
            detailMessage = "Saved reminder: $title • $date $time",
            actionCategory = ActionCategory.CREATE_REMINDER
        )
    }

    private fun handleCalendarEvent(action: JarvisCommandAction): CommandExecutionResult {
        val mode = action.parameters["mode"]?.lowercase() ?: "create"
        if (mode == "query" || mode == "read") {
            if (!permissionManager.isPermissionGranted(Manifest.permission.READ_CALENDAR)) {
                return CommandExecutionResult.PermissionRequired(
                    missingPermissions = listOf(Manifest.permission.READ_CALENDAR),
                    pendingAction = action,
                    explanation = "I need Calendar permission to check your upcoming schedule."
                )
            }
            return readTodayCalendarEvents()
        }

        val title = action.parameters["title"] ?: "Meeting"
        val hour = action.parameters["hour"]?.toIntOrNull() ?: 15
        val minute = action.parameters["minute"]?.toIntOrNull() ?: 0
        val dayOffset = action.parameters["day_offset"]?.toIntOrNull() ?: 1

        val beginCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
        val endTimeMillis = beginCal.timeInMillis + 60 * 60 * 1000L

        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginCal.timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandExecutionResult.Success(
                spokenMessage = "I've opened your Calendar to schedule '$title' at ${String.format(Locale.US, "%02d:%02d", hour, minute)}.",
                detailMessage = "Calendar event: $title",
                actionCategory = ActionCategory.CREATECALENDAREVENT
            )
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Could not launch a Calendar application.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.CREATECALENDAREVENT
            )
        }
    }

    private fun readTodayCalendarEvents(): CommandExecutionResult {
        return try {
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            val endOfDay = startOfDay + 24 * 60 * 60 * 1000L

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startOfDay)
            ContentUris.appendId(builder, endOfDay)

            val cursor = context.contentResolver.query(
                builder.build(),
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
            val events = mutableListOf<String>()
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            cursor?.use {
                while (it.moveToNext() && events.size < 5) {
                    val eventTitle = it.getString(0) ?: "Event"
                    val begin = it.getLong(1)
                    events.add("$eventTitle at ${timeFormat.format(Date(begin))}")
                }
            }

            if (events.isEmpty()) {
                CommandExecutionResult.Success(
                    spokenMessage = "Your calendar is clear for today. You have no scheduled appointments.",
                    detailMessage = "0 calendar events today",
                    actionCategory = ActionCategory.CREATECALENDAREVENT
                )
            } else {
                val summary = events.joinToString(", ")
                CommandExecutionResult.Success(
                    spokenMessage = "You have ${events.size} appointment${if (events.size > 1) "s" else ""} today: $summary.",
                    detailMessage = summary,
                    actionCategory = ActionCategory.CREATECALENDAREVENT
                )
            }
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Unable to read calendar events.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.CREATECALENDAREVENT
            )
        }
    }

    private fun handleControlVolume(action: JarvisCommandAction): CommandExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return CommandExecutionResult.Error(
                userFriendlyMessage = "Audio service is unavailable.",
                actionCategory = ActionCategory.CONTROL_VOLUME
            )

        val direction = action.parameters["direction"]?.lowercase() ?: "up"
        val targetPercent = action.parameters["level"]?.toIntOrNull()
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        return try {
            when {
                targetPercent != null -> {
                    val clamped = targetPercent.coerceIn(0, 100)
                    val newIndex = (clamped * maxVol) / 100
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        newIndex,
                        AudioManager.FLAG_SHOW_UI
                    )
                    CommandExecutionResult.Success(
                        spokenMessage = "Media volume set to $clamped percent.",
                        detailMessage = "Volume level: $newIndex / $maxVol ($clamped%)",
                        actionCategory = ActionCategory.CONTROL_VOLUME
                    )
                }
                direction.contains("mute") || direction.contains("silent") -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    CommandExecutionResult.Success(
                        spokenMessage = "Audio muted.",
                        detailMessage = "Stream Music Muted",
                        actionCategory = ActionCategory.CONTROL_VOLUME
                    )
                }
                direction.contains("down") || direction.contains("lower") || direction.contains("decrease") -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val pct = (current * 100) / maxVol
                    CommandExecutionResult.Success(
                        spokenMessage = "Volume turned down to $pct percent.",
                        detailMessage = "Volume: $pct%",
                        actionCategory = ActionCategory.CONTROL_VOLUME
                    )
                }
                else -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val pct = (current * 100) / maxVol
                    CommandExecutionResult.Success(
                        spokenMessage = "Volume increased to $pct percent.",
                        detailMessage = "Volume: $pct%",
                        actionCategory = ActionCategory.CONTROL_VOLUME
                    )
                }
            }
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Could not modify volume settings due to Do Not Disturb or audio policy.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.CONTROL_VOLUME
            )
        }
    }

    private fun handleControlBrightness(action: JarvisCommandAction): CommandExecutionResult {
        val levelPct = action.parameters["level"]?.toIntOrNull() ?: 75
        return if (Settings.System.canWrite(context)) {
            try {
                val brightnessValue = ((levelPct.coerceIn(5, 100) * 255) / 100)
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightnessValue
                )
                CommandExecutionResult.Success(
                    spokenMessage = "Display brightness set to $levelPct percent.",
                    detailMessage = "System brightness: $brightnessValue / 255",
                    actionCategory = ActionCategory.CONTROL_BRIGHTNESS
                )
            } catch (e: Exception) {
                openDisplaySettingsFallback()
            }
        } else {
            openDisplaySettingsFallback()
        }
    }

    private fun openDisplaySettingsFallback(): CommandExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandExecutionResult.RestrictedByAndroid(
                explanation = "I can't change screen brightness directly without 'Modify System Settings' permission, so I've opened Display Settings for you.",
                fallbackExecuted = "Opened Display Settings",
                actionCategory = ActionCategory.CONTROL_BRIGHTNESS
            )
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Could not open Display Settings.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.CONTROL_BRIGHTNESS
            )
        }
    }

    private fun handleOpenSettings(action: JarvisCommandAction): CommandExecutionResult {
        val panel = action.parameters["setting_type"]?.lowercase() ?: "main"
        val (intentAction, friendlyName, isRestrictedToggle) = when {
            panel.contains("wifi") || panel.contains("wi-fi") -> {
                val act = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_WIFI
                } else {
                    Settings.ACTION_WIFI_SETTINGS
                }
                Triple(act, "Wi-Fi Settings", panel.contains("off") || panel.contains("on") || panel.contains("toggle"))
            }
            panel.contains("bluetooth") -> Triple(
                Settings.ACTION_BLUETOOTH_SETTINGS,
                "Bluetooth Settings",
                panel.contains("off") || panel.contains("on")
            )
            panel.contains("mobile") || panel.contains("data") || panel.contains("network") || panel.contains("cellular") -> Triple(
                Settings.ACTION_DATA_ROAMING_SETTINGS,
                "Mobile Network Settings",
                true
            )
            panel.contains("display") || panel.contains("brightness") -> Triple(
                Settings.ACTION_DISPLAY_SETTINGS,
                "Display Settings",
                false
            )
            panel.contains("sound") || panel.contains("audio") -> Triple(
                Settings.ACTION_SOUND_SETTINGS,
                "Sound Settings",
                false
            )
            panel.contains("battery") || panel.contains("power") -> Triple(
                Intent.ACTION_POWER_USAGE_SUMMARY,
                "Battery Settings",
                false
            )
            panel.contains("location") || panel.contains("gps") -> Triple(
                Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                "Location Settings",
                panel.contains("off") || panel.contains("on")
            )
            else -> Triple(Settings.ACTION_SETTINGS, "System Settings", false)
        }

        return try {
            val intent = Intent(intentAction).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (isRestrictedToggle) {
                CommandExecutionResult.RestrictedByAndroid(
                    explanation = "I can't toggle $friendlyName directly because modern Android versions restrict background radio changes, so I've opened $friendlyName for you.",
                    fallbackExecuted = "Opened $friendlyName",
                    actionCategory = ActionCategory.OPEN_SETTINGS
                )
            } else {
                CommandExecutionResult.Success(
                    spokenMessage = "I've opened $friendlyName for you.",
                    detailMessage = "Navigated to $friendlyName",
                    actionCategory = ActionCategory.OPEN_SETTINGS
                )
            }
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                CommandExecutionResult.Success(
                    spokenMessage = "Opened Android Settings.",
                    detailMessage = "Fallback to root Settings",
                    actionCategory = ActionCategory.OPEN_SETTINGS
                )
            } catch (e: Exception) {
                CommandExecutionResult.Error(
                    userFriendlyMessage = "Unable to open Settings on this device.",
                    technicalDetail = e.message,
                    actionCategory = ActionCategory.OPEN_SETTINGS
                )
            }
        }
    }

    private fun handleMediaControl(action: JarvisCommandAction, isPlay: Boolean): CommandExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return CommandExecutionResult.Error(
                userFriendlyMessage = "Audio controller unavailable.",
                actionCategory = if (isPlay) ActionCategory.PLAY_MEDIA else ActionCategory.PAUSE_MEDIA
            )

        val subCommand = action.parameters["command"]?.lowercase().orEmpty()
        val songQuery = action.parameters["query"]?.trim().orEmpty()

        return try {
            if (songQuery.isNotEmpty()) {
                val searchPlayIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    putExtra(SearchManager.QUERY, songQuery)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (searchPlayIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(searchPlayIntent)
                } else {
                    val ytMusicUri = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(songQuery)}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, ytMusicUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                return CommandExecutionResult.Success(
                    spokenMessage = "Playing '$songQuery'.",
                    detailMessage = "Media search: $songQuery",
                    actionCategory = ActionCategory.PLAY_MEDIA
                )
            }

            val keyCode = when {
                subCommand.contains("next") || subCommand.contains("skip") -> KeyEvent.KEYCODE_MEDIA_NEXT
                subCommand.contains("prev") || subCommand.contains("back") -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                !isPlay || subCommand.contains("pause") || subCommand.contains("stop") -> KeyEvent.KEYCODE_MEDIA_PAUSE
                else -> KeyEvent.KEYCODE_MEDIA_PLAY
            }

            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))

            val spoken = when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> "Skipping to the next track."
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Going back to the previous track."
                KeyEvent.KEYCODE_MEDIA_PAUSE -> "Music paused."
                else -> "Resuming media playback."
            }
            CommandExecutionResult.Success(
                spokenMessage = spoken,
                detailMessage = "Dispatched Media KeyEvent ($keyCode)",
                actionCategory = if (isPlay) ActionCategory.PLAY_MEDIA else ActionCategory.PAUSE_MEDIA
            )
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Could not control media playback.",
                technicalDetail = e.message,
                actionCategory = if (isPlay) ActionCategory.PLAY_MEDIA else ActionCategory.PAUSE_MEDIA
            )
        }
    }

    private fun handleNavigate(action: JarvisCommandAction): CommandExecutionResult {
        val destination = action.parameters["destination"]?.trim().orEmpty()
        if (destination.isEmpty()) {
            return CommandExecutionResult.Error(
                userFriendlyMessage = "Please specify a destination or place to navigate to.",
                actionCategory = ActionCategory.NAVIGATE
            )
        }

        return try {
            val gmmUri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                val browserMaps = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(destination)}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserMaps)
            }
            CommandExecutionResult.Success(
                spokenMessage = "Navigating to $destination.",
                detailMessage = "Maps destination: $destination",
                actionCategory = ActionCategory.NAVIGATE
            )
        } catch (e: Exception) {
            CommandExecutionResult.Error(
                userFriendlyMessage = "Could not open Maps navigation.",
                technicalDetail = e.message,
                actionCategory = ActionCategory.NAVIGATE
            )
        }
    }

    private fun handleReadNotifications(action: JarvisCommandAction): CommandExecutionResult {
        if (!NotificationCache.isNotificationAccessEnabled(context)) {
            return CommandExecutionResult.PermissionRequired(
                missingPermissions = emptyList(),
                pendingAction = action,
                explanation = "I need your permission to access notifications before I can read them aloud.",
                specialSettingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            )
        }

        val items = NotificationCache.notifications.value
        return if (items.isEmpty()) {
            CommandExecutionResult.Success(
                spokenMessage = "You have no new notifications right now.",
                detailMessage = "Notification queue is empty",
                actionCategory = ActionCategory.READ_NOTIFICATION
            )
        } else {
            val topItems = items.take(3)
            val summary = topItems.joinToString(". ") { "${it.appName} says: ${it.title} ${it.text}".trim() }
            CommandExecutionResult.Success(
                spokenMessage = "You have ${items.size} notification${if (items.size > 1) "s" else ""}. $summary",
                detailMessage = "Read top ${topItems.size} notifications",
                actionCategory = ActionCategory.READ_NOTIFICATION
            )
        }
    }

    private fun handleDeviceInfo(action: JarvisCommandAction): CommandExecutionResult {
        val telemetry = getDeviceTelemetry()
        val queryType = action.parameters["info_type"]?.lowercase().orEmpty()

        val spoken = when {
            queryType.contains("battery") -> {
                val chargeStr = if (telemetry.isCharging) "and currently charging" else "on battery power"
                "Your battery is at ${telemetry.batteryPercentage} percent $chargeStr."
            }
            queryType.contains("time") || queryType.contains("date") -> {
                val now = SimpleDateFormat("EEEE, MMMM d, h:mm a", Locale.getDefault()).format(Date())
                "It is currently $now."
            }
            queryType.contains("memory") || queryType.contains("ram") -> {
                "System memory has ${telemetry.availableRamMb} megabytes free out of ${telemetry.totalRamMb} megabytes."
            }
            else -> {
                "Systems nominal on ${telemetry.deviceModel} running ${telemetry.androidVersion}. Battery is at ${telemetry.batteryPercentage}%, volume at ${telemetry.mediaVolumePercent}%, and network is ${telemetry.networkType}."
            }
        }

        return CommandExecutionResult.Success(
            spokenMessage = spoken,
            detailMessage = "${telemetry.deviceModel} • Battery ${telemetry.batteryPercentage}% • RAM ${telemetry.availableRamMb}/${telemetry.totalRamMb} MB",
            actionCategory = ActionCategory.DEVICE_INFORMATION
        )
    }

    private fun handleWeather(action: JarvisCommandAction): CommandExecutionResult {
        return CommandExecutionResult.Success(
            spokenMessage = action.spokenResponse,
            detailMessage = action.parameters["location"]?.let { "Forecast region: $it" } ?: "Weather Telemetry",
            actionCategory = ActionCategory.WEATHER
        )
    }
}
