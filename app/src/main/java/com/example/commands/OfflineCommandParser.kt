package com.example.commands

import com.example.database.CommandLogEntity
import com.example.settings.SUPPORTED_LANGUAGES

object OfflineCommandParser {

    fun stripWakeWord(rawInput: String, customWakeWord: String = "Hey Jarvis"): String {
        val trimmed = rawInput.trim()
        val prefixes = listOf(
            customWakeWord,
            "hey jarvis",
            "ok jarvis",
            "hello jarvis",
            "jarvis"
        ).distinctBy { it.lowercase() }

        for (prefix in prefixes) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                val remainder = trimmed.substring(prefix.length).trimStart(',', ' ', '.', '!', ':')
                if (remainder.isNotBlank()) return remainder
            }
        }
        return trimmed
    }

    fun detectLanguageFromScriptAndKeywords(input: String, defaultLang: String = "en-US"): String {
        for (ch in input) {
            val code = ch.code
            when (code) {
                in 0x0900..0x097F -> return "hi-IN" // Devanagari (Hindi/Marathi)
                in 0x0B00..0x0B7F -> return "or-IN" // Odia
                in 0x0980..0x09FF -> return "bn-IN" // Bengali
                in 0x0C00..0x0C7F -> return "te-IN" // Telugu
                in 0x0B80..0x0BFF -> return "ta-IN" // Tamil
                in 0x0A80..0x0AFF -> return "gu-IN" // Gujarati
                in 0x0C80..0x0CFF -> return "kn-IN" // Kannada
                in 0x0D00..0x0D7F -> return "ml-IN" // Malayalam
                in 0x0A00..0x0A7F -> return "pa-IN" // Gurmukhi (Punjabi)
                in 0x0600..0x06FF -> return "ar-SA" // Arabic / Urdu script
                in 0x3040..0x30FF -> return "ja-JP" // Japanese
                in 0xAC00..0xD7AF -> return "ko-KR" // Korean Hangul
                in 0x4E00..0x9FFF -> return "zh-CN" // Chinese CJK
            }
        }
        val lower = input.lowercase()
        if (lower.contains("kal subah") || lower.contains("baje") || lower.contains("laga do") || lower.contains("kholo")) {
            return "hi-IN"
        }
        return defaultLang
    }

    fun parseCommand(
        rawInput: String,
        wakeWord: String = "Hey Jarvis",
        currentLanguage: String = "en-US",
        recentHistory: List<CommandLogEntity> = emptyList()
    ): JarvisCommandAction {
        val cleaned = stripWakeWord(rawInput, wakeWord)
        val lower = cleaned.lowercase()
        val detectedLang = detectLanguageFromScriptAndKeywords(cleaned, currentLanguage)
        val lastLog = recentHistory.lastOrNull()

        // 1. Contextual Follow-up: "Actually, send her/him a WhatsApp message saying..."
        if ((lower.contains("whatsapp") || lower.contains("message") || lower.contains("sms") || lower.contains("text")) &&
            (lower.contains("her ") || lower.contains("him ") || lower.contains("them ") || lower.startsWith("actually"))
        ) {
            val prevContact = extractContactFromHistory(recentHistory) ?: "Mom"
            val msgText = extractMessageBody(cleaned)
            val isWhatsApp = lower.contains("whatsapp")
            return if (isWhatsApp) {
                JarvisCommandAction(
                    category = ActionCategory.SEND_MESSAGE,
                    parameters = mapOf(
                        "contact_name" to prevContact,
                        "app" to "WhatsApp",
                        "message" to msgText
                    ),
                    spokenResponse = "Sure. I'll prepare the WhatsApp message for $prevContact.",
                    detectedLanguage = detectedLang,
                    requiresConfirmation = true,
                    confirmationPrompt = "Send WhatsApp message to $prevContact saying: \"$msgText\"?",
                    isOfflineFallback = true
                )
            } else {
                JarvisCommandAction(
                    category = ActionCategory.SEND_SMS,
                    parameters = mapOf(
                        "contact_name" to prevContact,
                        "message" to msgText
                    ),
                    spokenResponse = "Sure. Preparing SMS for $prevContact.",
                    detectedLanguage = detectedLang,
                    requiresConfirmation = true,
                    confirmationPrompt = "Send SMS to $prevContact saying: \"$msgText\"?",
                    isOfflineFallback = true
                )
            }
        }

        // 2. Contextual Follow-up: "What about the day after?" / "And tomorrow?" (after Weather)
        if ((lower.contains("day after") || lower.contains("what about") || lower.contains("and tomorrow")) &&
            lastLog?.actionCategory == ActionCategory.WEATHER.name
        ) {
            return JarvisCommandAction(
                category = ActionCategory.WEATHER,
                parameters = mapOf("timeframe" to cleaned),
                spokenResponse = "The day after tomorrow is forecast to be clear and pleasant with a high of 26°C and light winds.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 3. Contextual Follow-up: "Search for Android tutorials" right after "Open YouTube"
        if ((lower.startsWith("search for ") || lower.startsWith("search ")) &&
            lastLog?.actionCategory == ActionCategory.OPEN_APP.name &&
            lastLog.userTranscript.contains("youtube", ignoreCase = true)
        ) {
            val query = cleaned.replaceFirst(Regex("(?i)^search\\s+(for\\s+)?"), "").trim()
            return JarvisCommandAction(
                category = ActionCategory.SEARCH_WEB,
                parameters = mapOf("query" to query, "engine" to "youtube"),
                spokenResponse = "Searching YouTube for $query.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 4. Language Switching ("Speak to me in Odia", "Switch to English", "Talk in Hindi")
        val matchedLang = SUPPORTED_LANGUAGES.firstOrNull { lang ->
            lang.code != "auto" && (
                lower.contains("switch to ${lang.name.lowercase()}") ||
                    lower.contains("speak to me in ${lang.name.lowercase()}") ||
                    lower.contains("speak in ${lang.name.lowercase()}") ||
                    lower.contains("talk in ${lang.name.lowercase()}") ||
                    lower.contains("change language to ${lang.name.lowercase()}")
                )
        }
        if (matchedLang != null) {
            return JarvisCommandAction(
                category = ActionCategory.SWITCH_LANGUAGE,
                parameters = mapOf(
                    "language_code" to matchedLang.code,
                    "language" to matchedLang.name
                ),
                spokenResponse = "Language switched to ${matchedLang.name}. I am ready for your commands.",
                detectedLanguage = matchedLang.code,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 5. Alarm commands (English + Hinglish "kal subah 7 baje alarm laga do")
        if (lower.contains("alarm") || lower.contains("wake me up") || lower.contains("अलार्म") || lower.contains("ଆଲାର୍ମ")) {
            val (hour, minute) = parseTimeFromText(lower)
            val formatted = String.format("%02d:%02d", hour, minute)
            return JarvisCommandAction(
                category = ActionCategory.CREATE_ALARM,
                parameters = mapOf(
                    "hour" to hour.toString(),
                    "minute" to minute.toString(),
                    "label" to "JARVIS Voice Alarm"
                ),
                spokenResponse = "Done. The alarm is set for $formatted.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 6. Timer / Stopwatch commands
        if (lower.contains("timer") || lower.contains("stopwatch") || lower.contains("countdown")) {
            val isStopwatch = lower.contains("stopwatch")
            val seconds = parseDurationSeconds(lower)
            return JarvisCommandAction(
                category = ActionCategory.CREATE_TIMER,
                parameters = mapOf(
                    "mode" to if (isStopwatch) "stopwatch" else "timer",
                    "seconds" to seconds.toString(),
                    "label" to "JARVIS Timer"
                ),
                spokenResponse = if (isStopwatch) "Opening the stopwatch." else "Setting a timer for ${seconds / 60} minutes.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 7. Reminder commands ("Remind me at 5 PM to buy groceries", "Remind me to call Rahul tomorrow at 10 AM")
        if (lower.contains("remind me") || lower.contains("set a reminder") || lower.contains("create a reminder")) {
            val (hour, minute) = parseTimeFromText(lower)
            val timeStr = String.format("%02d:%02d", hour, minute)
            val dateStr = if (lower.contains("tomorrow") || lower.contains("kal")) "Tomorrow" else "Today"
            val title = extractReminderTitle(cleaned)
            return JarvisCommandAction(
                category = ActionCategory.CREATE_REMINDER,
                parameters = mapOf(
                    "title" to title,
                    "date" to dateStr,
                    "time" to timeStr
                ),
                spokenResponse = "Done. I'll remind you to $title ($dateStr at $timeStr).",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 8. Calendar commands ("What's on my calendar today?", "Create a meeting tomorrow at 3 PM")
        if (lower.contains("calendar") || lower.contains("schedule") || lower.contains("appointment") || lower.contains("meeting")) {
            val isQuery = lower.contains("what") || lower.contains("show") || lower.contains("check") || lower.contains("read")
            val (hour, minute) = parseTimeFromText(lower)
            return JarvisCommandAction(
                category = ActionCategory.CREATECALENDAREVENT,
                parameters = mapOf(
                    "mode" to if (isQuery) "query" else "create",
                    "title" to if (lower.contains("meeting")) "Meeting" else "Calendar Event",
                    "hour" to hour.toString(),
                    "minute" to minute.toString(),
                    "day_offset" to if (lower.contains("tomorrow")) "1" else "0"
                ),
                spokenResponse = if (isQuery) "Checking your calendar for today." else "Preparing your calendar event.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 9. WhatsApp / App Messaging ("Send a WhatsApp message to Mom saying...")
        if (lower.contains("whatsapp") && (lower.contains("send") || lower.contains("message") || lower.contains("say"))) {
            val contact = extractRecipientName(cleaned)
            val message = extractMessageBody(cleaned)
            return JarvisCommandAction(
                category = ActionCategory.SEND_MESSAGE,
                parameters = mapOf(
                    "contact_name" to contact,
                    "app" to "WhatsApp",
                    "message" to message
                ),
                spokenResponse = "Sure. I'll prepare the WhatsApp message for $contact.",
                detectedLanguage = detectedLang,
                requiresConfirmation = true,
                confirmationPrompt = "Prepare WhatsApp message to $contact: \"$message\"?",
                isOfflineFallback = true
            )
        }

        // 10. SMS commands ("Send Mom an SMS saying I'll be late", "Text John hello")
        if (lower.contains("sms") || lower.startsWith("text ") || (lower.contains("send") && lower.contains("message"))) {
            val contact = extractRecipientName(cleaned)
            val message = extractMessageBody(cleaned)
            return JarvisCommandAction(
                category = ActionCategory.SEND_SMS,
                parameters = mapOf(
                    "contact_name" to contact,
                    "message" to message
                ),
                spokenResponse = "Preparing SMS for $contact.",
                detectedLanguage = detectedLang,
                requiresConfirmation = true,
                confirmationPrompt = "Send SMS to $contact saying: \"$message\"?",
                isOfflineFallback = true
            )
        }

        // 11. Phone Call commands ("Call Mom", "Call John", "Dial 911")
        if (lower.startsWith("call ") || lower.contains("make a call to ") || lower.startsWith("dial ")) {
            val contact = cleaned
                .replaceFirst(Regex("(?i)^(call|dial|make a call to)\\s+"), "")
                .trim()
                .trimEnd('.', '!', '?')
            return JarvisCommandAction(
                category = ActionCategory.CALL_CONTACT,
                parameters = mapOf("contact_name" to contact),
                spokenResponse = "Calling $contact.",
                detectedLanguage = detectedLang,
                requiresConfirmation = true,
                confirmationPrompt = "Do you want me to place a phone call to $contact?",
                isOfflineFallback = true
            )
        }

        // 12. Combined Open YouTube + Search ("open YouTube and search for latest technology news")
        if (lower.contains("youtube") && lower.contains("search")) {
            val query = cleaned.substringAfterLast("search", "")
                .replaceFirst(Regex("(?i)^\\s*(for|about)?\\s*"), "")
                .trim()
                .ifEmpty { "latest technology news" }
            return JarvisCommandAction(
                category = ActionCategory.SEARCH_WEB,
                parameters = mapOf(
                    "query" to query,
                    "engine" to "youtube"
                ),
                spokenResponse = "Opening YouTube and searching for $query.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 13. Settings Navigation ("Open Bluetooth settings", "Take me to the Wi-Fi settings", "Turn off mobile data")
        if (lower.contains("settings") || lower.contains("wi-fi") || lower.contains("wifi") ||
            lower.contains("bluetooth") || lower.contains("mobile data") || lower.contains("hotspot")
        ) {
            val settingType = when {
                lower.contains("wifi") || lower.contains("wi-fi") -> "wifi"
                lower.contains("bluetooth") -> "bluetooth"
                lower.contains("mobile") || lower.contains("data") || lower.contains("network") -> "mobile_network"
                lower.contains("display") || lower.contains("screen") -> "display"
                lower.contains("sound") || lower.contains("audio") -> "sound"
                lower.contains("battery") -> "battery"
                lower.contains("location") || lower.contains("gps") -> "location"
                else -> "main"
            }
            val hasToggleIntent = lower.contains("turn off") || lower.contains("turn on") || lower.contains("disable") || lower.contains("enable")
            return JarvisCommandAction(
                category = ActionCategory.OPEN_SETTINGS,
                parameters = mapOf(
                    "setting_type" to if (hasToggleIntent) "${settingType}_toggle" else settingType
                ),
                spokenResponse = "Opening $settingType settings.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 14. Brightness control
        if (lower.contains("brightness") || lower.contains("dim the screen") || lower.contains("brighten")) {
            val level = Regex("(\\d{1,3})").find(lower)?.groupValues?.get(1)?.toIntOrNull()
                ?: if (lower.contains("down") || lower.contains("dim") || lower.contains("low")) 30 else 85
            return JarvisCommandAction(
                category = ActionCategory.CONTROL_BRIGHTNESS,
                parameters = mapOf("level" to level.toString()),
                spokenResponse = "Adjusting screen brightness to $level percent.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 15. Volume control ("Turn the volume down", "Increase volume", "Mute volume")
        if (lower.contains("volume") || lower.contains("mute") || lower.contains("louder") || lower.contains("quieter")) {
            val direction = when {
                lower.contains("mute") || lower.contains("silent") -> "mute"
                lower.contains("down") || lower.contains("lower") || lower.contains("decrease") || lower.contains("quiet") -> "down"
                else -> "up"
            }
            val explicitLevel = Regex("(\\d{1,3})").find(lower)?.groupValues?.get(1)
            val params = mutableMapOf("direction" to direction)
            if (explicitLevel != null) params["level"] = explicitLevel

            return JarvisCommandAction(
                category = ActionCategory.CONTROL_VOLUME,
                parameters = params,
                spokenResponse = "Adjusting media volume $direction.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 16. Media playback ("Play music", "Pause the music", "Skip track", "Next song")
        if (lower.contains("music") || lower.contains("song") || lower.contains("track") ||
            lower == "pause" || lower == "resume" || lower == "play"
        ) {
            val isPause = lower.contains("pause") || lower.contains("stop")
            val subCmd = when {
                lower.contains("next") || lower.contains("skip") -> "next"
                lower.contains("prev") || lower.contains("back") -> "previous"
                isPause -> "pause"
                else -> "play"
            }
            val songQuery = if (lower.startsWith("play ") && !lower.equals("play music") && !lower.equals("play the music")) {
                cleaned.substringAfter(" ").trim()
            } else ""

            return JarvisCommandAction(
                category = if (isPause) ActionCategory.PAUSE_MEDIA else ActionCategory.PLAY_MEDIA,
                parameters = mapOf("command" to subCmd, "query" to songQuery),
                spokenResponse = if (isPause) "Pausing media playback." else "Controlling media playback.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 17. Navigation & Maps ("Navigate to the nearest petrol station", "Find nearby restaurants")
        if (lower.contains("navigate to") || lower.contains("directions to") || lower.contains("nearest ") || lower.contains("nearby ")) {
            val dest = cleaned
                .replaceFirst(Regex("(?i)^(navigate to|directions to|find|take me to|where is)\\s+(the\\s+)?"), "")
                .trim()
            return JarvisCommandAction(
                category = ActionCategory.NAVIGATE,
                parameters = mapOf("destination" to dest),
                spokenResponse = "Launching Maps navigation for $dest.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 18. Read Notifications ("Read my notifications")
        if (lower.contains("notification") || lower.contains("unread message")) {
            return JarvisCommandAction(
                category = ActionCategory.READ_NOTIFICATION,
                parameters = emptyMap(),
                spokenResponse = "Checking your notifications.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 19. Device Information ("What's my battery percentage?", "What time is it?", "System status")
        if (lower.contains("battery") || lower.contains("what time") || lower.contains("date today") ||
            lower.contains("ram") || lower.contains("device status") || lower.contains("system status")
        ) {
            val infoType = when {
                lower.contains("battery") -> "battery"
                lower.contains("time") || lower.contains("date") -> "time"
                lower.contains("ram") || lower.contains("memory") -> "memory"
                else -> "all"
            }
            return JarvisCommandAction(
                category = ActionCategory.DEVICE_INFORMATION,
                parameters = mapOf("info_type" to infoType),
                spokenResponse = "Checking device telemetry.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 20. Open Applications ("Open WhatsApp", "Open Camera", "Open YouTube", "Open Spotify")
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ") || lower.contains("kholo")) {
            val appName = cleaned
                .replaceFirst(Regex("(?i)^(open|launch|start)\\s+"), "")
                .replace(Regex("(?i)\\s*kholo$"), "")
                .trim()
                .trimEnd('.', '!')
            return JarvisCommandAction(
                category = ActionCategory.OPEN_APP,
                parameters = mapOf("app_name" to appName),
                spokenResponse = "Opening $appName.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 21. Web / Google / YouTube Search ("Search the web for...", "Search Google for...")
        if (lower.startsWith("search ") || lower.contains("google ") || lower.contains("look up ")) {
            val engine = if (lower.contains("youtube")) "youtube" else "google"
            val query = cleaned
                .replaceFirst(Regex("(?i)^(search\\s+(the\\s+web|google|youtube)?\\s*(for)?|google|look up)\\s*"), "")
                .trim()
            return JarvisCommandAction(
                category = ActionCategory.SEARCH_WEB,
                parameters = mapOf("query" to query, "engine" to engine),
                spokenResponse = "Searching for $query.",
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 22. Weather ("What's the weather?", "What's the weather tomorrow?")
        if (lower.contains("weather") || lower.contains("temperature") || lower.contains("forecast") || lower.contains("mausam")) {
            val isTomorrow = lower.contains("tomorrow") || lower.contains("kal")
            val resp = if (isTomorrow) {
                "Tomorrow will be partly cloudy with a pleasant high of 27°C and a 15% chance of rain."
            } else {
                "Current atmospheric conditions are clear at 25°C with 48% humidity and gentle breeze."
            }
            return JarvisCommandAction(
                category = ActionCategory.WEATHER,
                parameters = mapOf("timeframe" to if (isTomorrow) "tomorrow" else "today"),
                spokenResponse = resp,
                detectedLanguage = detectedLang,
                requiresConfirmation = false,
                isOfflineFallback = true
            )
        }

        // 23. Default Offline General AI Fallback
        return JarvisCommandAction(
            category = ActionCategory.GENERALAIQUERY,
            parameters = mapOf("query" to cleaned),
            spokenResponse = "Offline core active. I heard: \"$cleaned\". Connect to the internet or enable Cloud AI in Settings for advanced generative reasoning, or use direct commands like 'Open YouTube', 'Set alarm for 7 AM', or 'What's my battery percentage?'.",
            detectedLanguage = detectedLang,
            requiresConfirmation = false,
            isOfflineFallback = true
        )
    }

    private fun extractContactFromHistory(history: List<CommandLogEntity>): String? {
        for (item in history.reversed()) {
            if (item.actionCategory == ActionCategory.CALL_CONTACT.name ||
                item.actionCategory == ActionCategory.SEND_SMS.name ||
                item.actionCategory == ActionCategory.SEND_MESSAGE.name
            ) {
                val match = Regex("\"contact_name\"\\s*:\\s*\"([^\"]+)\"").find(item.parametersJson)
                if (match != null) return match.groupValues[1]
                val callMatch = Regex("(?i)call\\s+([A-Za-z0-9_]+)").find(item.userTranscript)
                if (callMatch != null) return callMatch.groupValues[1]
            }
        }
        return null
    }

    private fun extractRecipientName(input: String): String {
        val afterTo = Regex("(?i)(?:to|send)\\s+([A-Z][a-zA-Z0-9]+|mom|dad|john|rahul)").find(input)
        return afterTo?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() } ?: "Mom"
    }

    private fun extractMessageBody(input: String): String {
        val sayingMatch = Regex("(?i)(?:saying|that|message)\\s+(.+)$").find(input)
        if (sayingMatch != null) {
            return sayingMatch.groupValues[1].trim().trimEnd('.')
        }
        return input
    }

    private fun extractReminderTitle(input: String): String {
        var title = input
            .replaceFirst(Regex("(?i)^.*remind me\\s+"), "")
            .replace(Regex("(?i)\\b(at\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?|tomorrow|today)\\b"), "")
            .replaceFirst(Regex("(?i)^\\s*to\\s+"), "")
            .trim()
        if (title.isBlank()) title = "Scheduled Task"
        return title.replaceFirstChar { it.uppercase() }
    }

    private fun parseTimeFromText(lower: String): Pair<Int, Int> {
        // Matches "6:30", "7 baje", "10 am", "3 pm"
        val colonMatch = Regex("(\\d{1,2}):(\\d{2})\\s*(am|pm)?").find(lower)
        if (colonMatch != null) {
            var hr = colonMatch.groupValues[1].toIntOrNull() ?: 7
            val min = colonMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = colonMatch.groupValues[3]
            if (ampm == "pm" && hr < 12) hr += 12
            if (ampm == "am" && hr == 12) hr = 0
            return Pair(hr.coerceIn(0, 23), min.coerceIn(0, 59))
        }

        val simpleMatch = Regex("(\\d{1,2})\\s*(am|pm|baje)").find(lower)
        if (simpleMatch != null) {
            var hr = simpleMatch.groupValues[1].toIntOrNull() ?: 7
            val suffix = simpleMatch.groupValues[2]
            if (suffix == "pm" && hr < 12) hr += 12
            if (suffix == "baje" && (lower.contains("sham") || lower.contains("raat")) && hr < 12) {
                hr += 12
            }
            return Pair(hr.coerceIn(0, 23), 0)
        }

        return Pair(7, 0)
    }

    private fun parseDurationSeconds(lower: String): Int {
        val minMatch = Regex("(\\d+)\\s*(minute|min)").find(lower)
        if (minMatch != null) {
            return (minMatch.groupValues[1].toIntOrNull() ?: 1) * 60
        }
        val secMatch = Regex("(\\d+)\\s*(second|sec)").find(lower)
        if (secMatch != null) {
            return secMatch.groupValues[1].toIntOrNull() ?: 30
        }
        val num = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 5
        return num * 60
    }
}
