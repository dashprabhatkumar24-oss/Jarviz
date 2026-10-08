package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class VoiceOrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    CONFIRMATION
}

class JarvisVoiceManager(
    private val context: Context,
    private val onFinalTranscript: (String) -> Unit,
    private val onVoiceError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _livePartialTranscript = MutableStateFlow("")
    val livePartialTranscript: StateFlow<String> = _livePartialTranscript.asStateFlow()

    private val _audioLevelRms = MutableStateFlow(0f)
    val audioLevelRms: StateFlow<Float> = _audioLevelRms.asStateFlow()

    private val _availableTtsLanguages = MutableStateFlow<List<String>>(emptyList())
    val availableTtsLanguages: StateFlow<List<String>> = _availableTtsLanguages.asStateFlow()

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            isTtsReady = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })
            val locales = try {
                textToSpeech?.availableLanguages?.map { it.toLanguageTag() }?.sorted() ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            _availableTtsLanguages.value = locales
        }
    }

    fun startListening(languageCode: String = "en-US", preferOffline: Boolean = false) {
        // Interrupt TTS immediately if JARVIS is currently speaking
        stopSpeaking()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onVoiceError("Speech recognition service is not installed or enabled on this device. You can type or tap any command chip below.")
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            }

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                val targetLocale = if (languageCode == "auto" || languageCode.isBlank()) {
                    Locale.getDefault().toLanguageTag()
                } else {
                    languageCode
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (preferOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            _livePartialTranscript.value = ""
            _isListening.value = true
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            _isListening.value = false
            onVoiceError("Could not start microphone recognition: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        _isListening.value = false
        _audioLevelRms.value = 0f
    }

    fun speak(
        text: String,
        languageCode: String = "en-US",
        speechRate: Float = 1.0f,
        pitch: Float = 0.95f,
        ttsEnabled: Boolean = true
    ) {
        if (!ttsEnabled || text.isBlank() || !isTtsReady) {
            _isSpeaking.value = false
            return
        }

        try {
            val locale = if (languageCode == "auto" || languageCode.isBlank()) {
                Locale.US
            } else {
                Locale.forLanguageTag(languageCode)
            }
            textToSpeech?.language = locale
            textToSpeech?.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
            textToSpeech?.setPitch(pitch.coerceIn(0.5f, 1.8f))

            val utteranceId = UUID.randomUUID().toString()
            _isSpeaking.value = true
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (_: Exception) {
            _isSpeaking.value = false
        }
    }

    fun stopSpeaking() {
        try {
            if (textToSpeech?.isSpeaking == true) {
                textToSpeech?.stop()
            }
        } catch (_: Exception) {
        }
        _isSpeaking.value = false
    }

    fun shutdown() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Normalize typical -2..10 dB to 0f..1f for HUD orb animation
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                _audioLevelRms.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                _audioLevelRms.value = 0f
            }

            override fun onError(error: Int) {
                _isListening.value = false
                _audioLevelRms.value = 0f
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that clearly. Please try speaking again or tap a command."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the orb whenever you're ready."
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone audio capture error. Check if another app is using the mic."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice commands."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network speech recognition unavailable. Switching to offline command mode."
                    else -> "Voice recognition paused (code $error). You can retry or use text commands."
                }
                onVoiceError(message)
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                _audioLevelRms.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val topResult = matches?.firstOrNull()?.trim().orEmpty()
                if (topResult.isNotEmpty()) {
                    _livePartialTranscript.value = topResult
                    onFinalTranscript(topResult)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotEmpty()) {
                    _livePartialTranscript.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}
