/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.lovixtv.subtitle

import android.content.Context
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient

/**
 * Central coordinator for live subtitle generation.
 *
 * Orchestrates the full pipeline:
 * 1. Audio samples are fed in via [feedAudio]
 * 2. VOSK recognizes speech and produces text
 * 3. LibreTranslate translates to the target language
 * 4. Resulting subtitle cues are emitted via [subtitleCues]
 *
 * The engine manages its own coroutine scope for background processing
 * and exposes a simple Flow-based API for the UI to consume.
 */
class LiveSubtitleEngine(
    private val context: Context,
    private val httpClient: OkHttpClient,
) : Closeable {

    sealed class State {
        object Idle : State()
        object Initializing : State()
        object DownloadingModel : State()
        object Ready : State()
        object Processing : State()
        data class Error(val message: String) : State()
    }

    data class SubtitleCue(
        val originalText: String,
        val translatedText: String,
        val timestamp: Long,
    )

    data class Config(
        val recognitionLanguage: String = "en",
        val displayLanguage: String = "en",
        val enabled: Boolean = false,
    )

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _subtitleCues = MutableSharedFlow<SubtitleCue>(replay = 1)
    val subtitleCues: SharedFlow<SubtitleCue> = _subtitleCues

    private val _currentConfig = MutableStateFlow(Config())
    val currentConfig: StateFlow<Config> = _currentConfig.asStateFlow()

    private val modelDownloader = ModelDownloader(context, httpClient)
    private val voskRecognizer = VoskRecognizer(context)
    private val translator = LibreTranslator(httpClient)
    private val audioRecorder = AudioRecorder(this)

    private var isRunning = false
    private var recognitionJob: Job? = null

    // Map display language codes to VOSK model info
    private val modelMap = mapOf(
        "en" to ModelDownloader.ENGLISH,
        "es" to ModelDownloader.SPANISH,
        "nl" to ModelDownloader.DUTCH,
        "fr" to ModelDownloader.ENGLISH, // Fallback to English model if French not available
        "de" to ModelDownloader.ENGLISH, // Fallback to English model if German not available
    )

    // Map display language codes to VOSK recognition language codes
    private val recognitionLangMap = mapOf(
        "en" to "en-us",
        "es" to "es",
        "nl" to "nl",
        "fr" to "en-us", // Fallback
        "de" to "en-us", // Fallback
    )

    /**
     * Start the subtitle engine with the given configuration.
     */
    suspend fun start(config: Config) {
        if (!config.enabled) return

        _currentConfig.value = config
        _state.value = State.Initializing

        try {
            // Download the model if needed
            val modelInfo = modelMap[config.recognitionLanguage] ?: modelMap["en"]!!
            if (!modelDownloader.isModelAvailable(modelInfo)) {
                _state.value = State.DownloadingModel
                modelDownloader.ensureModel(modelInfo)
            }

            // Initialize VOSK recognizer with the correct language code
            val modelDir = File(context.filesDir, "vosk_models/${modelInfo.name}")
            val recognitionLang = recognitionLangMap[config.recognitionLanguage] ?: "en-us"
            val success = voskRecognizer.initialize(modelDir, recognitionLang)

            if (success) {
                _state.value = State.Ready
                isRunning = true
                startRecognitionLoop()
                // Start audio recording to feed VOSK
                audioRecorder.start()
            } else {
                _state.value = State.Error("Failed to initialize speech recognition")
            }
        } catch (e: Exception) {
            _state.value = State.Error("Error starting subtitles: ${e.message}")
        }
    }

    /**
     * Stop the subtitle engine and release resources.
     */
    fun stop() {
        isRunning = false
        audioRecorder.stop()
        recognitionJob?.cancel()
        voskRecognizer.shutdown()
        _state.value = State.Idle
    }

    /**
     * Feed audio data to the recognizer.
     *
     * @param audioData PCM 16-bit audio at 16kHz mono
     */
    fun feedAudio(audioData: ShortArray) {
        if (!isRunning || !voskRecognizer.isReady()) return

        val text = voskRecognizer.recognize(audioData)
        if (text.isNotBlank()) {
            processText(text)
        }
    }

    /**
     * Update the display language without restarting recognition.
     */
    fun setDisplayLanguage(language: String) {
        _currentConfig.value = _currentConfig.value.copy(displayLanguage = language)
    }

    /**
     * Toggle subtitles on/off.
     */
    fun toggleEnabled() {
        val current = _currentConfig.value
        if (current.enabled) {
            stop()
            _currentConfig.value = current.copy(enabled = false)
        } else {
            engineScope.launch {
                start(current.copy(enabled = true))
            }
        }
    }

    /**
     * Cycle through available display languages.
     */
    fun cycleLanguage(): String {
        val current = _currentConfig.value
        val languages = listOf("off", "en", "es", "nl", "fr", "de")
        val currentIndex = languages.indexOf(current.displayLanguage)
        val nextIndex = (currentIndex + 1) % languages.size
        val nextLang = languages[nextIndex]

        if (nextLang == "off") {
            stop()
            _currentConfig.value = current.copy(enabled = false, displayLanguage = "off")
        } else {
            _currentConfig.value = current.copy(
                enabled = true,
                displayLanguage = nextLang
            )
            if (_state.value == State.Idle) {
                engineScope.launch { start(_currentConfig.value) }
            }
        }
        return nextLang
    }

    private fun startRecognitionLoop() {
        // The recognition loop is a heartbeat that keeps the engine alive.
        // Actual audio is fed in via feedAudio() from the audio recording thread.
        recognitionJob = engineScope.launch {
            while (isActive && isRunning) {
                delay(500)
            }
        }
    }

    private fun processText(text: String) {
        val config = _currentConfig.value
        if (config.recognitionLanguage == config.displayLanguage) {
            // No translation needed
            _subtitleCues.tryEmit(SubtitleCue(
                originalText = text,
                translatedText = text,
                timestamp = System.currentTimeMillis(),
            ))
        } else {
            // Translate asynchronously
            engineScope.launch {
                _state.value = State.Processing
                val translated = translator.translate(
                    text = text,
                    sourceLang = config.recognitionLanguage,
                    targetLang = config.displayLanguage,
                )
                _subtitleCues.emit(SubtitleCue(
                    originalText = text,
                    translatedText = translated,
                    timestamp = System.currentTimeMillis(),
                ))
                if (isRunning) _state.value = State.Ready
            }
        }
    }

    override fun close() {
        stop()
        audioRecorder.stop()
        engineScope.cancel()
    }
}
