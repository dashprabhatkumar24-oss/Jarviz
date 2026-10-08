package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_preferences")

data class SupportedLanguage(
    val code: String,
    val name: String,
    val nativeName: String,
    val sampleCommand: String
)

val SUPPORTED_LANGUAGES = listOf(
    SupportedLanguage("auto", "Auto-Detect", "Automatic / Mixed", "Hey Jarvis, open YouTube"),
    SupportedLanguage("en-US", "English", "English", "Hey Jarvis, what's my battery percentage?"),
    SupportedLanguage("hi-IN", "Hindi", "हिन्दी / Hinglish", "Jarvis, kal subah 7 baje alarm laga do"),
    SupportedLanguage("or-IN", "Odia", "ଓଡ଼ିଆ", "Jarvis, ସକାଳ ୭ ଟାରେ ଆଲାର୍ମ ଲଗାନ୍ତୁ"),
    SupportedLanguage("bn-IN", "Bengali", "বাংলা", "Jarvis, আগামীকাল সকাল ৭টায় অ্যালার্ম সেট করো"),
    SupportedLanguage("te-IN", "Telugu", "తెలుగు", "Jarvis, రేపు ఉదయం 7 గంటలకు అలారం పెట్టు"),
    SupportedLanguage("ta-IN", "Tamil", "தமிழ்", "Jarvis, நாளை காலை 7 மணிக்கு அலாரம் வை"),
    SupportedLanguage("mr-IN", "Marathi", "मराठी", "Jarvis, उद्या सकाळी ७ वाजता अलार्म लाव"),
    SupportedLanguage("gu-IN", "Gujarati", "ગુજરાતી", "Jarvis, કાલે સવારે 7 વાગ્યે એલાર્म મૂકો"),
    SupportedLanguage("kn-IN", "Kannada", "ಕನ್ನಡ", "Jarvis, ನಾಳೆ ಬೆಳಿಗ್ಗೆ 7 ಗಂಟೆಗೆ ಅಲಾರಂ ಇಡಿ"),
    SupportedLanguage("ml-IN", "Malayalam", "മലയാളം", "Jarvis, നാളെ രാവിലെ 7 മണിക്ക് അലാറം വെക്കുക"),
    SupportedLanguage("pa-IN", "Punjabi", "ਪੰਜਾਬੀ", "Jarvis, ਕੱਲ੍ਹ ਸਵੇਰੇ 7 ਵਜੇ ਅਲਾਰਮ ਲਗਾਓ"),
    SupportedLanguage("ur-PK", "Urdu", "اردو", "جاروس، کل صبح 7 بجے کا الارم لگا دو"),
    SupportedLanguage("es-ES", "Spanish", "Español", "Jarvis, pon una alarma mañana a las 7 AM"),
    SupportedLanguage("fr-FR", "French", "Français", "Jarvis, règle une alarme pour demain à 7h"),
    SupportedLanguage("de-DE", "German", "Deutsch", "Jarvis, stelle einen Wecker für morgen um 7 Uhr"),
    SupportedLanguage("it-IT", "Italian", "Italiano", "Jarvis, imposta una sveglia per domani alle 7"),
    SupportedLanguage("pt-BR", "Portuguese", "Português", "Jarvis, defina um alarme para amanhã às 7h"),
    SupportedLanguage("ar-SA", "Arabic", "العربية", "جارفيس، اضبط المنبه غدا الساعة 7 صباحا"),
    SupportedLanguage("ja-JP", "Japanese", "日本語", "ジャービス、明日の朝7時にアラームをセットして"),
    SupportedLanguage("ko-KR", "Korean", "한국어", "자비스, 내일 아침 7시에 알람 맞춰줘"),
    SupportedLanguage("zh-CN", "Chinese", "中文", "贾维斯，设置明天早上7点的闹钟")
)

enum class PersonalityPreset(val displayName: String, val systemPrompt: String) {
    TACTICAL_JARVIS(
        "Tactical JARVIS (Default)",
        "Calm, intelligent, concise, respectful, slightly futuristic, and helpful. Never overly verbose. Address the user respectfully."
    ),
    CONCISE_HUD(
        "Ultra-Concise HUD",
        "Direct, telemetry-style responses in 1 sentence maximum. Zero filler words."
    ),
    FRIENDLY_COMPANION(
        "Warm & Conversational",
        "Warm, encouraging, approachable, and clear while remaining sharp and capable."
    ),
    TECHNICAL_ANALYST(
        "Technical Diagnostics",
        "Detailed, analytical, and precise. Explain system states, parameters, and Android API behaviors clearly."
    )
}

data class JarvisPreferences(
    val assistantName: String = "JARVIS",
    val wakeWord: String = "Hey Jarvis",
    val wakeWordEnabled: Boolean = true,
    val selectedLanguageCode: String = "en-US",
    val autoDetectLanguage: Boolean = true,
    val personalityPreset: PersonalityPreset = PersonalityPreset.TACTICAL_JARVIS,
    val speechRate: Float = 1.0f,
    val voicePitch: Float = 0.95f,
    val ttsEnabled: Boolean = true,
    val cloudAiEnabled: Boolean = true,
    val offlineFallbackEnabled: Boolean = true,
    val confirmSensitiveActions: Boolean = true,
    val backgroundServiceEnabled: Boolean = false,
    val autoPauseLowBattery: Boolean = true,
    val darkThemeEnabled: Boolean = true
)

class JarvisSettingsRepository(private val context: Context) {

    private object Keys {
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val AUTO_DETECT_LANG = booleanPreferencesKey("auto_detect_lang")
        val PERSONALITY = stringPreferencesKey("personality")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val CLOUD_AI_ENABLED = booleanPreferencesKey("cloud_ai_enabled")
        val OFFLINE_FALLBACK = booleanPreferencesKey("offline_fallback")
        val CONFIRM_SENSITIVE = booleanPreferencesKey("confirm_sensitive")
        val BACKGROUND_SERVICE = booleanPreferencesKey("background_service")
        val AUTO_PAUSE_BATTERY = booleanPreferencesKey("auto_pause_battery")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val preferencesFlow: Flow<JarvisPreferences> = context.dataStore.data.map { prefs ->
        val personalityName = prefs[Keys.PERSONALITY] ?: PersonalityPreset.TACTICAL_JARVIS.name
        val personality = PersonalityPreset.entries.find { it.name == personalityName }
            ?: PersonalityPreset.TACTICAL_JARVIS

        JarvisPreferences(
            assistantName = prefs[Keys.ASSISTANT_NAME] ?: "JARVIS",
            wakeWord = prefs[Keys.WAKE_WORD] ?: "Hey Jarvis",
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: true,
            selectedLanguageCode = prefs[Keys.LANGUAGE_CODE] ?: "en-US",
            autoDetectLanguage = prefs[Keys.AUTO_DETECT_LANG] ?: true,
            personalityPreset = personality,
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 0.95f,
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            cloudAiEnabled = prefs[Keys.CLOUD_AI_ENABLED] ?: true,
            offlineFallbackEnabled = prefs[Keys.OFFLINE_FALLBACK] ?: true,
            confirmSensitiveActions = prefs[Keys.CONFIRM_SENSITIVE] ?: true,
            backgroundServiceEnabled = prefs[Keys.BACKGROUND_SERVICE] ?: false,
            autoPauseLowBattery = prefs[Keys.AUTO_PAUSE_BATTERY] ?: true,
            darkThemeEnabled = prefs[Keys.DARK_THEME] ?: true
        )
    }

    suspend fun updateAssistantName(name: String) {
        context.dataStore.edit { it[Keys.ASSISTANT_NAME] = name.ifBlank { "JARVIS" } }
    }

    suspend fun updateWakeWord(wakeWord: String) {
        context.dataStore.edit { it[Keys.WAKE_WORD] = wakeWord.ifBlank { "Hey Jarvis" } }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun updateLanguage(languageCode: String) {
        context.dataStore.edit {
            if (languageCode == "auto") {
                it[Keys.AUTO_DETECT_LANG] = true
            } else {
                it[Keys.LANGUAGE_CODE] = languageCode
            }
        }
    }

    suspend fun setAutoDetectLanguage(autoDetect: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DETECT_LANG] = autoDetect }
    }

    suspend fun updatePersonality(preset: PersonalityPreset) {
        context.dataStore.edit { it[Keys.PERSONALITY] = preset.name }
    }

    suspend fun updateSpeechRate(rate: Float) {
        context.dataStore.edit { it[Keys.SPEECH_RATE] = rate.coerceIn(0.5f, 2.0f) }
    }

    suspend fun updateVoicePitch(pitch: Float) {
        context.dataStore.edit { it[Keys.VOICE_PITCH] = pitch.coerceIn(0.5f, 1.8f) }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }

    suspend fun setCloudAiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLOUD_AI_ENABLED] = enabled }
    }

    suspend fun setConfirmSensitiveActions(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CONFIRM_SENSITIVE] = enabled }
    }

    suspend fun setBackgroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_SERVICE] = enabled }
    }

    suspend fun setAutoPauseLowBattery(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PAUSE_BATTERY] = enabled }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun resetAllSettings() {
        context.dataStore.edit { it.clear() }
    }
}
