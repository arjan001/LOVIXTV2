/*
 * Subtitle state management for real-time subtitles.
 * Manages subtitle language, display state, and real-time text.
 */
package app.lovixtv.ui.player

import androidx.compose.runtime.mutableStateOf

class SubtitleState {
    val enabled = mutableStateOf(false)
    val language = mutableStateOf("off") // off, en, es, nl
    val currentText = mutableStateOf("")
    val translatedText = mutableStateOf("")
    val modelDownloaded = mutableStateOf(false)
    val isDownloading = mutableStateOf(false)
    
    fun cycleLanguage() {
        language.value = when (language.value) {
            "off" -> "en"
            "en" -> "es"
            "es" -> "nl"
            "nl" -> "off"
            else -> "off"
        }
        enabled.value = language.value != "off"
    }
    
    fun getLanguageName(): String {
        return when (language.value) {
            "off" -> "Off"
            "en" -> "English"
            "es" -> "Spanish"
            "nl" -> "Dutch"
            else -> "Off"
        }
    }
}
