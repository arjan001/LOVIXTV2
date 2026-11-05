# Vosk Subtitle Setup for LOVIXTV2 Android App

This document explains how to set up Vosk speech recognition for offline subtitle generation in the LOVIXTV2 Android app.

## Overview

The LOVIXTV2 app uses:
- **Vosk** for offline speech recognition (English, Spanish, Dutch)
- **LibreTranslate** for subtitle translation (local or remote server)

## Vosk Model Setup

### 1. Download Vosk Models

Download the Vosk models and place them in the app's assets or external storage:

```bash
# English model (small, ~50MB)
wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip vosk-model-small-en-us-0.15.zip

# Spanish model (if needed)
wget https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip
unzip vosk-model-small-es-0.42.zip

# Dutch model (if needed)
wget https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip
unzip vosk-model-small-nl-0.22.zip
```

### 2. Model Placement

Models should be placed in the app's private storage directory:
- Path: `/data/data/app.lovixtv/files/vosk-models/`
- English: `vosk-model-en/`
- Spanish: `vosk-model-es/`
- Dutch: `vosk-model-nl/`

### 3. Alternative: Bundle Models in APK

For a self-contained APK, include models in the app's assets:
- Path: `app/src/main/assets/vosk-models/`
- Copy model directories to assets folder
- Extract to app storage on first launch

## LibreTranslate Setup

### Option 1: Local Server (Recommended for Privacy)

Run LibreTranslate locally on your network:

```bash
# Install LibreTranslate
pip install LibreTranslate[stanza,alt]

# Run LibreTranslate (load only English, Spanish, Dutch)
libretranslate --host 0.0.0.0 --port 5000 --load-only en,es,nl
```

Configure the app to use your local server URL:
- Default: `http://localhost:5000`
- Network: `http://YOUR_SERVER_IP:5000`

### Option 2: Remote LibreTranslate Service

Use a hosted LibreTranslate service:
- Set server URL in app settings
- Ensure service supports en, es, nl languages

## Integration in App

### 1. Initialize Vosk Service

```kotlin
val voskService = VoskSubtitleService(context)
val initialized = voskService.initialize("en")
```

### 2. Initialize LibreTranslate Service

```kotlin
val libreTranslateService = LibreTranslateService(
    httpClient = okHttpClient,
    serverUrl = "http://YOUR_SERVER:5000"
)
```

### 3. Generate Subtitles

```kotlin
// Transcribe audio
val segments = voskService.transcribe(audioStream)

// Translate if needed
val translatedSegments = libreTranslateService.translateSegments(
    segments,
    source = "en",
    target = "es"
)
```

## Supported Languages

- **Speech Recognition (Vosk):** English, Spanish, Dutch
- **Translation (LibreTranslate):** English ↔ Spanish, English ↔ Dutch

## Performance Considerations

- Vosk small models: ~50MB each
- Transcription speed: ~0.5-1x real-time on modern devices
- Memory usage: ~100-200MB for model + recognition
- Battery impact: Moderate (CPU-based processing)

## Troubleshooting

### Model Not Found
- Ensure model directory exists in app storage
- Check file permissions
- Verify model extraction from assets if bundled

### Transcription Poor Quality
- Use Vosk "small" or "base" models for better accuracy
- Ensure audio is 16kHz mono PCM
- Check audio quality and noise levels

### Translation Fails
- Verify LibreTranslate server is running
- Check network connectivity to translation server
- Ensure target language is supported

## Building the APK

The app will include Vosk dependencies automatically:
- `com.alphacephei:vosk-android:0.3.45`

Models must be downloaded separately and placed in storage or bundled in assets.
