# JARVIS Voice AI — Multilingual Android Personal Assistant

JARVIS Voice AI is a futuristic, privacy-first, multilingual AI voice assistant and smartphone controller built with Kotlin, Jetpack Compose, Material Design 3, Room, DataStore, and Gemini 3.5 Flash structured tool-calling.

## Architecture & Modules

- **`/ui`**: Futuristic JARVIS HUD interface (`HudDashboardScreen`, `CommandCenterScreen`, `PermissionsScreen`, `SettingsPrivacyScreen`, and custom Canvas `JarvisHudOrb`). Supports adaptive layouts (`NavigationBar` on phones, `NavigationRail` on tablets/foldables) and both Dark OLED HUD and Daylight themes.
- **`/voice`**: `JarvisVoiceManager` wrapping Android's native `SpeechRecognizer` (with real-time partial transcription and RMS audio-reactive orb animation) and `TextToSpeech` (with instant speech interruption on tap or new command). Includes `JarvisForegroundService` with battery-saver auto-pause (<15%) and a clear microphone privacy indicator notification.
- **`/ai`**: `GeminiAiEngine` integrating `gemini-3.5-flash` via Retrofit & `kotlinx.serialization` with strict JSON Schema output (`responseSchema`) so the LLM can only invoke registered `ActionCategory` tools. Automatically falls back to `OfflineCommandParser` when offline or when Cloud AI is disabled.
- **`/commands`**: Centralized command framework (`ActionCategory`, `JarvisCommandAction`, `CommandExecutionResult`, `OfflineCommandParser`) supporting 20 structured action categories, contextual follow-ups (pronoun resolution for `"send her a WhatsApp message"`, `"what about the day after?"`, `"search for Android tutorials"`), and multilingual/Hinglish/Odia parsing.
- **`/android`**: `AndroidSystemController` executing official Android APIs and Intents (Phone Calls, Dialer, Contacts lookup, SMS, WhatsApp deep links, AlarmClock, Timers, Stopwatch, Calendar queries & inserts, AudioManager volume & Media KeyEvents, Maps navigation, Web/YouTube search, and Settings panel fallbacks).
- **`/permissions`**: `JarvisPermissionManager` providing on-demand zero-trust runtime permission checks and clear explanations before any protected resource is accessed.
- **`/notifications`**: `JarvisNotificationListenerService` for reading incoming notifications aloud only when explicitly permitted by the user.
- **`/database` & `/settings`**: Room database (`JarvisDatabase`) for command telemetry logs and local reminders, plus Jetpack DataStore (`JarvisSettingsRepository`) for persona, wake-word, speech rate/pitch, language, and privacy toggles.

## API Configuration Instructions

1. Open the **Secrets panel in Google AI Studio**.
2. Add your `GEMINI_API_KEY` secret. The project's `.env.example` is pre-configured with `GEMINI_API_KEY` so the Secrets Gradle Plugin injects `BuildConfig.GEMINI_API_KEY` at build time.
3. No API key is required for the **Offline Core** — alarms, timers, reminders, app launching, volume control, device telemetry, settings navigation, and multilingual Hinglish/Odia/English commands work 100% offline out of the box.
