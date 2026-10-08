package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiAiEngine
import com.example.android.AndroidSystemController
import com.example.android.DeviceTelemetry
import com.example.android.InstalledAppInfo
import com.example.commands.ActionCategory
import com.example.commands.CommandExecutionResult
import com.example.commands.JarvisCommandAction
import com.example.database.CommandLogEntity
import com.example.database.JarvisDatabase
import com.example.database.JarvisRepository
import com.example.database.ReminderEntity
import com.example.permissions.JarvisPermissionManager
import com.example.permissions.PermissionItemInfo
import com.example.settings.JarvisPreferences
import com.example.settings.JarvisSettingsRepository
import com.example.settings.PersonalityPreset
import com.example.voice.ForegroundServiceState
import com.example.voice.JarvisForegroundService
import com.example.voice.JarvisVoiceManager
import com.example.voice.VoiceOrbState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class PendingPermissionPrompt(
    val missingPermissions: List<String>,
    val pendingAction: JarvisCommandAction,
    val explanation: String,
    val specialSettingsAction: String? = null
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = JarvisDatabase.getInstance(context)
    val repository = JarvisRepository(database.jarvisDao())
    val settingsRepository = JarvisSettingsRepository(context)
    val permissionManager = JarvisPermissionManager(context)
    val systemController = AndroidSystemController(context, permissionManager, repository)
    val aiEngine = GeminiAiEngine()

    val preferences: StateFlow<JarvisPreferences> = settingsRepository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JarvisPreferences()
    )

    val commandLogs: StateFlow<List<CommandLogEntity>> = repository.commandLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val reminders: StateFlow<List<ReminderEntity>> = repository.reminders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _orbState = MutableStateFlow(VoiceOrbState.IDLE)
    val orbState: StateFlow<VoiceOrbState> = _orbState.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    private val _latestAssistantMessage = MutableStateFlow(
        "Good morning. All systems are online and ready for your voice or text commands."
    )
    val latestAssistantMessage: StateFlow<String> = _latestAssistantMessage.asStateFlow()

    private val _latestActionDetail = MutableStateFlow<String?>("Ready • Say \"Hey Jarvis\" or tap the orb")
    val latestActionDetail: StateFlow<String?> = _latestActionDetail.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<CommandExecutionResult.RequiresConfirmation?>(null)
    val pendingConfirmation: StateFlow<CommandExecutionResult.RequiresConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _pendingPermissionPrompt = MutableStateFlow<PendingPermissionPrompt?>(null)
    val pendingPermissionPrompt: StateFlow<PendingPermissionPrompt?> = _pendingPermissionPrompt.asStateFlow()

    private val _deviceTelemetry = MutableStateFlow(systemController.getDeviceTelemetry())
    val deviceTelemetry: StateFlow<DeviceTelemetry> = _deviceTelemetry.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _permissionStatuses = MutableStateFlow<List<PermissionItemInfo>>(emptyList())
    val permissionStatuses: StateFlow<List<PermissionItemInfo>> = _permissionStatuses.asStateFlow()

    val isForegroundServiceRunning = ForegroundServiceState.isRunning
    val isPausedForLowBattery = ForegroundServiceState.isPausedForLowBattery

    val voiceManager = JarvisVoiceManager(
        context = context,
        onFinalTranscript = { recognizedText ->
            processCommand(recognizedText)
        },
        onVoiceError = { errorMsg ->
            _orbState.value = VoiceOrbState.IDLE
            _latestAssistantMessage.value = errorMsg
        }
    )

    val audioLevelRms = voiceManager.audioLevelRms
    val livePartialTranscript = voiceManager.livePartialTranscript

    init {
        refreshSystemStatus()
        viewModelScope.launch {
            voiceManager.isListening.collect { listening ->
                if (listening) {
                    _orbState.value = VoiceOrbState.LISTENING
                } else if (_orbState.value == VoiceOrbState.LISTENING) {
                    _orbState.value = VoiceOrbState.IDLE
                }
            }
        }
        viewModelScope.launch {
            voiceManager.isSpeaking.collect { speaking ->
                if (speaking) {
                    _orbState.value = VoiceOrbState.SPEAKING
                } else if (_orbState.value == VoiceOrbState.SPEAKING) {
                    _orbState.value = if (_pendingConfirmation.value != null) {
                        VoiceOrbState.CONFIRMATION
                    } else {
                        VoiceOrbState.IDLE
                    }
                }
            }
        }
    }

    fun refreshSystemStatus() {
        _deviceTelemetry.value = systemController.getDeviceTelemetry()
        _permissionStatuses.value = permissionManager.getAllPermissionStatuses()
        viewModelScope.launch {
            _installedApps.value = systemController.discoverInstalledApps()
        }
    }

    fun onOrbOrMicTapped(onRequestMicPermission: () -> Unit) {
        // If speaking, tapping immediately interrupts speech
        if (voiceManager.isSpeaking.value) {
            voiceManager.stopSpeaking()
            _orbState.value = VoiceOrbState.IDLE
            _latestActionDetail.value = "Speech interrupted by user"
            return
        }

        // If already listening, stop listening
        if (voiceManager.isListening.value) {
            voiceManager.stopListening()
            _orbState.value = VoiceOrbState.IDLE
            return
        }

        // Check RECORD_AUDIO permission on-demand before starting SpeechRecognizer
        if (!permissionManager.isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            onRequestMicPermission()
            return
        }

        val prefs = preferences.value
        val telemetry = systemController.getDeviceTelemetry()
        if (prefs.autoPauseLowBattery && telemetry.batteryPercentage < 15 && !telemetry.isCharging) {
            _latestAssistantMessage.value =
                "Battery is critically low (${telemetry.batteryPercentage}%). Continuous listening is paused to conserve power, or you can use text commands."
        }

        voiceManager.startListening(
            languageCode = if (prefs.autoDetectLanguage) "auto" else prefs.selectedLanguageCode,
            preferOffline = !telemetry.isNetworkOnline || !prefs.cloudAiEnabled
        )
    }

    fun processCommand(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return

        // Interrupt any ongoing speech when a new command arrives
        voiceManager.stopSpeaking()
        _currentTranscript.value = trimmed
        _orbState.value = VoiceOrbState.THINKING

        viewModelScope.launch {
            val prefs = preferences.value
            val telemetry = systemController.getDeviceTelemetry()
            _deviceTelemetry.value = telemetry
            val recentLogs = repository.getRecentConversationContext(6)

            val parsedAction = aiEngine.interpretUserCommand(
                userTranscript = trimmed,
                preferences = prefs,
                recentHistory = recentLogs,
                isNetworkOnline = telemetry.isNetworkOnline
            )

            // Handle dynamic language switching if the command was SWITCH_LANGUAGE
            if (parsedAction.category == ActionCategory.SWITCH_LANGUAGE) {
                val targetCode = parsedAction.parameters["language_code"] ?: parsedAction.detectedLanguage
                settingsRepository.updateLanguage(targetCode)
            }

            executeAndRespond(
                userTranscript = trimmed,
                action = parsedAction,
                userConfirmed = false
            )
        }
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            executeAndRespond(
                userTranscript = _currentTranscript.value.ifEmpty { pending.pendingAction.category.title },
                action = pending.pendingAction,
                userConfirmed = true
            )
        }
    }

    fun cancelPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        _orbState.value = VoiceOrbState.IDLE
        val cancelMsg = "Action cancelled. Standing by."
        _latestAssistantMessage.value = cancelMsg
        _latestActionDetail.value = "Aborted ${pending.pendingAction.category.title}"
        speakOut(cancelMsg, pending.pendingAction.detectedLanguage)
        viewModelScope.launch {
            repository.logInteraction(
                userTranscript = _currentTranscript.value,
                assistantResponse = cancelMsg,
                actionCategory = pending.pendingAction.category.name,
                status = "CANCELLED"
            )
        }
    }

    fun dismissPermissionPrompt() {
        _pendingPermissionPrompt.value = null
        _orbState.value = VoiceOrbState.IDLE
    }

    fun retryAfterPermissionGranted() {
        val prompt = _pendingPermissionPrompt.value ?: return
        _pendingPermissionPrompt.value = null
        refreshSystemStatus()
        viewModelScope.launch {
            executeAndRespond(
                userTranscript = _currentTranscript.value,
                action = prompt.pendingAction,
                userConfirmed = true
            )
        }
    }

    private suspend fun executeAndRespond(
        userTranscript: String,
        action: JarvisCommandAction,
        userConfirmed: Boolean
    ) {
        val prefs = preferences.value
        val result = systemController.executeAction(
            action = action,
            userConfirmed = userConfirmed,
            requireConfirmationPolicy = prefs.confirmSensitiveActions
        )

        val paramsJson = try {
            Json.encodeToString(action.parameters)
        } catch (_: Exception) {
            "{}"
        }

        when (result) {
            is CommandExecutionResult.RequiresConfirmation -> {
                _pendingConfirmation.value = result
                _orbState.value = VoiceOrbState.CONFIRMATION
                _latestAssistantMessage.value = result.reason
                _latestActionDetail.value = "Awaiting authorization for ${action.category.title}"
                speakOut(result.reason, action.detectedLanguage)
            }

            is CommandExecutionResult.PermissionRequired -> {
                _pendingPermissionPrompt.value = PendingPermissionPrompt(
                    missingPermissions = result.missingPermissions,
                    pendingAction = result.pendingAction,
                    explanation = result.explanation,
                    specialSettingsAction = result.specialSettingsAction
                )
                _orbState.value = VoiceOrbState.IDLE
                _latestAssistantMessage.value = result.explanation
                _latestActionDetail.value = "Permission required: ${action.category.title}"
                speakOut(result.explanation, action.detectedLanguage)
                repository.logInteraction(
                    userTranscript = userTranscript,
                    assistantResponse = result.explanation,
                    actionCategory = action.category.name,
                    parametersJson = paramsJson,
                    languageCode = action.detectedLanguage,
                    wasOffline = action.isOfflineFallback,
                    status = "PERMISSION_NEEDED"
                )
            }

            is CommandExecutionResult.RestrictedByAndroid -> {
                _orbState.value = VoiceOrbState.IDLE
                _latestAssistantMessage.value = result.explanation
                _latestActionDetail.value = result.fallbackExecuted
                speakOut(result.explanation, action.detectedLanguage)
                repository.logInteraction(
                    userTranscript = userTranscript,
                    assistantResponse = result.explanation,
                    actionCategory = action.category.name,
                    parametersJson = paramsJson,
                    languageCode = action.detectedLanguage,
                    wasOffline = action.isOfflineFallback,
                    status = "RESTRICTED_FALLBACK"
                )
            }

            is CommandExecutionResult.Success -> {
                _orbState.value = VoiceOrbState.IDLE
                _latestAssistantMessage.value = result.spokenMessage
                _latestActionDetail.value = result.detailMessage ?: "Executed ${action.category.title}"
                speakOut(result.spokenMessage, action.detectedLanguage)
                repository.logInteraction(
                    userTranscript = userTranscript,
                    assistantResponse = result.spokenMessage,
                    actionCategory = action.category.name,
                    parametersJson = paramsJson,
                    languageCode = action.detectedLanguage,
                    wasOffline = action.isOfflineFallback,
                    status = "SUCCESS"
                )
                refreshSystemStatus()
            }

            is CommandExecutionResult.Error -> {
                _orbState.value = VoiceOrbState.IDLE
                _latestAssistantMessage.value = result.userFriendlyMessage
                _latestActionDetail.value = result.technicalDetail ?: "Execution error"
                speakOut(result.userFriendlyMessage, action.detectedLanguage)
                repository.logInteraction(
                    userTranscript = userTranscript,
                    assistantResponse = result.userFriendlyMessage,
                    actionCategory = action.category.name,
                    parametersJson = paramsJson,
                    languageCode = action.detectedLanguage,
                    wasOffline = action.isOfflineFallback,
                    status = "ERROR"
                )
            }
        }
    }

    private fun speakOut(message: String, languageCode: String) {
        val prefs = preferences.value
        val targetLang = if (languageCode.isNotBlank() && languageCode != "auto") {
            languageCode
        } else {
            prefs.selectedLanguageCode
        }
        voiceManager.speak(
            text = message,
            languageCode = targetLang,
            speechRate = prefs.speechRate,
            pitch = prefs.voicePitch,
            ttsEnabled = prefs.ttsEnabled
        )
    }

    fun toggleForegroundVoiceGuard(enable: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackgroundServiceEnabled(enable)
            val serviceIntent = Intent(context, JarvisForegroundService::class.java)
            if (enable) {
                if (permissionManager.isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (_: Exception) {
                    }
                }
            } else {
                serviceIntent.action = JarvisForegroundService.ACTION_STOP_SERVICE
                try {
                    context.startService(serviceIntent)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun toggleReminderCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.toggleReminder(id, completed)
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            repository.deleteReminder(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearConversationHistory()
            _latestActionDetail.value = "Conversation history cleared"
        }
    }

    fun wipeAllData() {
        viewModelScope.launch {
            repository.wipeAllLocalData()
            settingsRepository.resetAllSettings()
            _latestAssistantMessage.value = "All local memory, logs, reminders, and settings have been wiped."
            _latestActionDetail.value = "Memory wipe complete"
        }
    }

    fun updateAssistantName(name: String) = viewModelScope.launch {
        settingsRepository.updateAssistantName(name)
    }

    fun updateWakeWord(word: String) = viewModelScope.launch {
        settingsRepository.updateWakeWord(word)
    }

    fun setWakeWordEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setWakeWordEnabled(enabled)
    }

    fun updateLanguage(code: String) = viewModelScope.launch {
        settingsRepository.updateLanguage(code)
    }

    fun updatePersonality(preset: PersonalityPreset) = viewModelScope.launch {
        settingsRepository.updatePersonality(preset)
    }

    fun updateSpeechRate(rate: Float) = viewModelScope.launch {
        settingsRepository.updateSpeechRate(rate)
    }

    fun updateVoicePitch(pitch: Float) = viewModelScope.launch {
        settingsRepository.updateVoicePitch(pitch)
    }

    fun setTtsEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setTtsEnabled(enabled)
        if (!enabled) voiceManager.stopSpeaking()
    }

    fun setCloudAiEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setCloudAiEnabled(enabled)
    }

    fun setConfirmSensitiveActions(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setConfirmSensitiveActions(enabled)
    }

    fun setAutoPauseLowBattery(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoPauseLowBattery(enabled)
    }

    fun setDarkThemeEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDarkThemeEnabled(enabled)
    }

    override fun onCleared() {
        voiceManager.shutdown()
        super.onCleared()
    }
}
