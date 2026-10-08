package com.example.ai

import com.example.BuildConfig
import com.example.commands.ActionCategory
import com.example.commands.JarvisCommandAction
import com.example.commands.OfflineCommandParser
import com.example.database.CommandLogEntity
import com.example.settings.JarvisPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val role: String? = "user",
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    val temperature: Float? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList()
)

@Serializable
data class Candidate(
    val content: Content? = null
)

interface GeminiRestApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

class GeminiAiEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val apiService: GeminiRestApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiRestApiService::class.java)
    }

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun interpretUserCommand(
        userTranscript: String,
        preferences: JarvisPreferences,
        recentHistory: List<CommandLogEntity>,
        isNetworkOnline: Boolean
    ): JarvisCommandAction = withContext(Dispatchers.IO) {
        val cleanedInput = OfflineCommandParser.stripWakeWord(userTranscript, preferences.wakeWord)

        // If Cloud AI is disabled by privacy toggle, offline, or no valid API key configured, use local deterministic NLU
        if (!preferences.cloudAiEnabled || !isNetworkOnline || !isApiKeyConfigured()) {
            return@withContext OfflineCommandParser.parseCommand(
                rawInput = cleanedInput,
                wakeWord = preferences.wakeWord,
                currentLanguage = preferences.selectedLanguageCode,
                recentHistory = recentHistory
            )
        }

        val allowedCategories = ActionCategory.entries.joinToString(", ") { it.name }
        val historyContext = if (recentHistory.isEmpty()) {
            "No prior turns in this session."
        } else {
            recentHistory.takeLast(5).joinToString("\n") {
                "User: ${it.userTranscript} -> Action: ${it.actionCategory} (${it.parametersJson}) -> Assistant: ${it.assistantResponse}"
            }
        }

        val systemInstructionText = """
            You are ${preferences.assistantName}, a futuristic, calm, intelligent, concise personal AI assistant for Android smartphones inspired by JARVIS.
            Personality directive: ${preferences.personalityPreset.systemPrompt}
            Preferred language code: ${preferences.selectedLanguageCode} (Auto-detect spoken language if user speaks another language or mixed Hinglish/Odia/Spanish/etc., and respond in the appropriate language).
            
            You control the user's Android smartphone ONLY through structured registered tools.
            Allowed action categories: [$allowedCategories].
            
            Recent Conversation Context (use this to resolve pronouns like "her", "him", "the day after", or follow-up searches):
            $historyContext
            
            Parameter keys by action category:
            - CALL_CONTACT: contact_name, phone_number
            - SEND_SMS: contact_name, phone_number, message
            - SEND_MESSAGE: contact_name, app (e.g. WhatsApp), message
            - OPEN_APP: app_name (e.g. YouTube, WhatsApp, Chrome, Camera, Gallery, Maps, Spotify)
            - SEARCH_WEB: query, engine (google or youtube)
            - CREATE_ALARM: hour (0-23), minute (0-59), label
            - CREATE_TIMER: mode (timer or stopwatch), seconds, label
            - CREATE_REMINDER: title, date (e.g. Tomorrow, Today), time (e.g. 10:00)
            - CREATECALENDAREVENT: mode (create or query), title, hour (0-23), minute (0-59), day_offset (0 for today, 1 for tomorrow)
            - CONTROL_VOLUME: direction (up, down, mute), level (0-100 optional)
            - CONTROL_BRIGHTNESS: level (0-100)
            - OPEN_SETTINGS: setting_type (wifi, bluetooth, mobile_network, display, sound, battery, location, main)
            - PLAY_MEDIA: command (play, next, previous), query (song name optional)
            - PAUSE_MEDIA: command (pause)
            - NAVIGATE: destination
            - READ_NOTIFICATION: (no required params)
            - DEVICE_INFORMATION: info_type (battery, time, memory, all)
            - WEATHER: timeframe (today, tomorrow, day_after), location
            - SWITCH_LANGUAGE: language (e.g. Odia, Hindi, English, Spanish), language_code (e.g. or-IN, hi-IN, en-US, es-ES)
            - GENERALAIQUERY: query
            
            Security Rule: Set requiresConfirmation = true for CALL_CONTACT, SEND_SMS, SEND_MESSAGE, or any sensitive/irreversible operation.
        """.trimIndent()

        val schema = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("action") {
                    put("type", "STRING")
                    put("description", "One of the allowed ActionCategory enum names.")
                }
                putJsonObject("spokenResponse") {
                    put("type", "STRING")
                    put("description", "Concise, naturalJARVIS-style spoken reply in the user's language.")
                }
                putJsonObject("detectedLanguage") {
                    put("type", "STRING")
                    put("description", "BCP-47 language tag detected from user input, e.g. en-US, hi-IN, or-IN, es-ES.")
                }
                putJsonObject("requiresConfirmation") {
                    put("type", "BOOLEAN")
                    put("description", "True if this action calls someone, sends a message, or is sensitive.")
                }
                putJsonObject("confirmationPrompt") {
                    put("type", "STRING")
                    put("description", "Clear confirmation question if requiresConfirmation is true.")
                }
                putJsonObject("contact_name") { put("type", "STRING") }
                putJsonObject("phone_number") { put("type", "STRING") }
                putJsonObject("message") { put("type", "STRING") }
                putJsonObject("app") { put("type", "STRING") }
                putJsonObject("app_name") { put("type", "STRING") }
                putJsonObject("query") { put("type", "STRING") }
                putJsonObject("engine") { put("type", "STRING") }
                putJsonObject("hour") { put("type", "STRING") }
                putJsonObject("minute") { put("type", "STRING") }
                putJsonObject("seconds") { put("type", "STRING") }
                putJsonObject("label") { put("type", "STRING") }
                putJsonObject("title") { put("type", "STRING") }
                putJsonObject("date") { put("type", "STRING") }
                putJsonObject("time") { put("type", "STRING") }
                putJsonObject("mode") { put("type", "STRING") }
                putJsonObject("direction") { put("type", "STRING") }
                putJsonObject("level") { put("type", "STRING") }
                putJsonObject("setting_type") { put("type", "STRING") }
                putJsonObject("command") { put("type", "STRING") }
                putJsonObject("destination") { put("type", "STRING") }
                putJsonObject("info_type") { put("type", "STRING") }
                putJsonObject("language") { put("type", "STRING") }
                putJsonObject("language_code") { put("type", "STRING") }
            }
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    role = "user",
                    parts = listOf(Part(text = cleanedInput))
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.2f
            ),
            systemInstruction = Content(
                role = "system",
                parts = listOf(Part(text = systemInstructionText))
            )
        )

        return@withContext try {
            val response = apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
            val rawJson = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (rawJson.isNullOrBlank()) {
                OfflineCommandParser.parseCommand(cleanedInput, preferences.wakeWord, preferences.selectedLanguageCode, recentHistory)
            } else {
                parseStructuredJsonToAction(rawJson, cleanedInput, preferences)
            }
        } catch (_: Exception) {
            // Automatic seamless fallback to offline parser if network or API call fails
            OfflineCommandParser.parseCommand(
                rawInput = cleanedInput,
                wakeWord = preferences.wakeWord,
                currentLanguage = preferences.selectedLanguageCode,
                recentHistory = recentHistory
            )
        }
    }

    private fun parseStructuredJsonToAction(
        rawJson: String,
        originalTranscript: String,
        preferences: JarvisPreferences
    ): JarvisCommandAction {
        return try {
            val obj = json.parseToJsonElement(rawJson).jsonObject
            val actionStr = obj["action"]?.jsonPrimitive?.content?.uppercase() ?: "GENERALAIQUERY"
            val category = ActionCategory.entries.find { it.name == actionStr }
                ?: ActionCategory.GENERALAIQUERY

            val spoken = obj["spokenResponse"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: "Command processed."
            val detectedLang = obj["detectedLanguage"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: preferences.selectedLanguageCode
            val reqConfirm = obj["requiresConfirmation"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: category.defaultRequiresConfirmation
            val confirmPrompt = obj["confirmationPrompt"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

            val paramKeys = listOf(
                "contact_name", "phone_number", "message", "app", "app_name",
                "query", "engine", "hour", "minute", "seconds", "label",
                "title", "date", "time", "mode", "direction", "level",
                "setting_type", "command", "destination", "info_type",
                "language", "language_code"
            )
            val params = mutableMapOf<String, String>()
            for (k in paramKeys) {
                val v = obj[k]?.jsonPrimitive?.content
                if (!v.isNullOrBlank() && v != "null") {
                    params[k] = v
                }
            }

            JarvisCommandAction(
                category = category,
                parameters = params,
                spokenResponse = spoken,
                detectedLanguage = detectedLang,
                requiresConfirmation = reqConfirm,
                confirmationPrompt = confirmPrompt,
                isOfflineFallback = false
            )
        } catch (_: Exception) {
            OfflineCommandParser.parseCommand(
                rawInput = originalTranscript,
                wakeWord = preferences.wakeWord,
                currentLanguage = preferences.selectedLanguageCode
            )
        }
    }
}
