/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.lovixtv.subtitle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest

/**
 * Subtitle overlay displayed on top of the video player.
 *
 * Shows the current subtitle text with a semi-transparent background for readability.
 * Supports language cycling and displays the current language indicator.
 */
@Composable
fun SubtitleOverlay(
    engine: LiveSubtitleEngine,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsState()
    val config by engine.currentConfig.collectAsState()
    var currentCue by remember { mutableStateOf<LiveSubtitleEngine.SubtitleCue?>(null) }
    var showLanguageIndicator by remember { mutableStateOf(true) }
    var languageIndicatorText by remember { mutableStateOf("") }

    // Collect subtitle cues
    LaunchedEffect(engine) {
        engine.subtitleCues.collectLatest { cue ->
            currentCue = cue
        }
    }

    // Show language indicator when language changes
    LaunchedEffect(config.displayLanguage) {
        if (config.displayLanguage != "off" && config.displayLanguage.isNotEmpty()) {
            showLanguageIndicator = true
            languageIndicatorText = LibreTranslator.LANGUAGE_NAMES[config.displayLanguage]
                ?: config.displayLanguage.uppercase()
            kotlinx.coroutines.delay(2000)
            showLanguageIndicator = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // Language indicator at top center
        AnimatedVisibility(
            visible = showLanguageIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
        ) {
            Surface(
                modifier = Modifier.padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.75f),
            ) {
                Text(
                    text = "Subtitles: $languageIndicatorText",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        // Subtitle text at bottom center
        AnimatedVisibility(
            visible = currentCue != null && config.enabled,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .padding(horizontal = 40.dp),
        ) {
            currentCue?.let { cue ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    tonalElevation = 2.dp,
                ) {
                    Text(
                        text = cue.translatedText,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .wrapContentWidth(),
                    )
                }
            }
        }

        // Status indicator (downloading, processing, error)
        AnimatedVisibility(
            visible = state is LiveSubtitleEngine.State.DownloadingModel ||
                      state is LiveSubtitleEngine.State.Processing ||
                      state is LiveSubtitleEngine.State.Error,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            when (state) {
                is LiveSubtitleEngine.State.DownloadingModel -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Downloading model...",
                                color = Color.White,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                is LiveSubtitleEngine.State.Processing -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color.White.copy(alpha = 0.7f),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Translating...",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                is LiveSubtitleEngine.State.Error -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE53935).copy(alpha = 0.8f),
                    ) {
                        Text(
                            text = (state as LiveSubtitleEngine.State.Error).message,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

/**
 * Subtitle language button for the player controls.
 *
 * Cycles through: Off → English → Spanish → Dutch → French → German → Off
 */
@Composable
fun SubtitleLanguageButton(
    engine: LiveSubtitleEngine,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val config by engine.currentConfig.collectAsState()
    val state by engine.state.collectAsState()

    val isOn = config.enabled && config.displayLanguage != "off"
    val currentLang = if (isOn) {
        LibreTranslator.LANGUAGE_NAMES[config.displayLanguage] ?: config.displayLanguage.uppercase()
    } else "Off"

    var focused by remember { mutableStateOf(false) }
    val containerColor = if (focused) MaterialTheme.colorScheme.primary
    else Color.White.copy(alpha = 0.14f)
    val contentColor = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(containerColor)
            .then(
                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Subtitles,
            contentDescription = "Subtitles",
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = currentLang,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}
