package com.example.commands

import kotlinx.serialization.Serializable

enum class ActionCategory(
    val title: String,
    val description: String,
    val defaultRequiresConfirmation: Boolean,
    val requiredRuntimePermissions: List<String>
) {
    CALL_CONTACT(
        title = "Make Phone Call",
        description = "Initiate a voice call or open the dialer with a contact or number",
        defaultRequiresConfirmation = true,
        requiredRuntimePermissions = listOf(
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.CALL_PHONE
        )
    ),
    SEND_SMS(
        title = "Send SMS Message",
        description = "Compose or dispatch an SMS text message to a contact or phone number",
        defaultRequiresConfirmation = true,
        requiredRuntimePermissions = listOf(
            android.Manifest.permission.READ_CONTACTS
        )
    ),
    SEND_MESSAGE(
        title = "Send App Message",
        description = "Prepare and open a message in WhatsApp or another messaging app",
        defaultRequiresConfirmation = true,
        requiredRuntimePermissions = listOf(
            android.Manifest.permission.READ_CONTACTS
        )
    ),
    OPEN_APP(
        title = "Launch Application",
        description = "Discover and launch an installed Android application or system camera/gallery",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    SEARCH_WEB(
        title = "Web / YouTube Search",
        description = "Search Google, YouTube, or open web results via Android Intents",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    CREATE_ALARM(
        title = "Set Alarm",
        description = "Schedule a system clock alarm for a specific hour and minute",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    CREATE_TIMER(
        title = "Set Timer / Stopwatch",
        description = "Start a countdown timer or open the system stopwatch",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    CREATE_REMINDER(
        title = "Create Reminder",
        description = "Save a local reminder with scheduled date and time in JARVIS memory",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    CREATECALENDAREVENT(
        title = "Calendar Event",
        description = "Create a new calendar meeting or view today's schedule",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = listOf(
            android.Manifest.permission.READ_CALENDAR
        )
    ),
    CONTROL_VOLUME(
        title = "Volume Control",
        description = "Adjust media, ring, or alarm volume levels using AudioManager",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    CONTROL_BRIGHTNESS(
        title = "Display Brightness",
        description = "Adjust screen brightness or open Display Settings if Write Settings is restricted",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    OPEN_SETTINGS(
        title = "System Settings Navigation",
        description = "Open specific Android system settings panels (Wi-Fi, Bluetooth, Network, Battery, etc.)",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    PLAY_MEDIA(
        title = "Play / Skip Media",
        description = "Play, resume, skip, or search music on the active media session",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    PAUSE_MEDIA(
        title = "Pause / Stop Media",
        description = "Pause or stop currently playing audio/video media",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    NAVIGATE(
        title = "Maps & Turn-by-Turn Navigation",
        description = "Launch Google Maps or navigation to a destination or nearby place",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    READ_NOTIFICATION(
        title = "Read Notifications",
        description = "Read recent incoming notifications aloud with explicit Notification Listener access",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    DEVICE_INFORMATION(
        title = "Device Telemetry & Status",
        description = "Check real-time battery level, charging state, network status, RAM/storage, and time",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    WEATHER(
        title = "Weather & Forecast",
        description = "Provide current or upcoming weather forecast and atmospheric conditions",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    SWITCH_LANGUAGE(
        title = "Language Switch",
        description = "Switch JARVIS voice recognition and speech synthesis language dynamically",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    ),
    GENERALAIQUERY(
        title = "General Intelligence & Translation",
        description = "Answer complex questions, translate phrases, summarize, or hold contextual conversation",
        defaultRequiresConfirmation = false,
        requiredRuntimePermissions = emptyList()
    )
}

@Serializable
data class JarvisCommandAction(
    val category: ActionCategory,
    val parameters: Map<String, String> = emptyMap(),
    val spokenResponse: String,
    val detectedLanguage: String = "en-US",
    val requiresConfirmation: Boolean = category.defaultRequiresConfirmation,
    val confirmationPrompt: String? = null,
    val isOfflineFallback: Boolean = false
)

sealed class CommandExecutionResult {
    data class Success(
        val spokenMessage: String,
        val detailMessage: String? = null,
        val actionCategory: ActionCategory
    ) : CommandExecutionResult()

    data class RequiresConfirmation(
        val pendingAction: JarvisCommandAction,
        val reason: String
    ) : CommandExecutionResult()

    data class PermissionRequired(
        val missingPermissions: List<String>,
        val pendingAction: JarvisCommandAction,
        val explanation: String,
        val specialSettingsAction: String? = null
    ) : CommandExecutionResult()

    data class RestrictedByAndroid(
        val explanation: String,
        val fallbackExecuted: String,
        val actionCategory: ActionCategory
    ) : CommandExecutionResult()

    data class Error(
        val userFriendlyMessage: String,
        val technicalDetail: String? = null,
        val actionCategory: ActionCategory
    ) : CommandExecutionResult()
}
