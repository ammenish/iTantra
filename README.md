<p align="center">
  <img src="docs/banner.png" alt="iTantra Banner" width="800"/>
</p>

<h1 align="center">📡 iTantra</h1>

<p align="center">
  <b>Offline Multilingual Voice Communication System for Android</b><br/>
  <i>Speak in any Indian language → Transmit as text → Hear it translated on the other end</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android&logoColor=white" alt="Android 14+"/>
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.0"/>
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/>
  <img src="https://img.shields.io/badge/License-MIT-blue" alt="MIT License"/>
  <img src="https://img.shields.io/badge/Languages-10_Indian-orange" alt="10 Languages"/>
</p>

---

## 🎯 What is iTantra?

**iTantra** is a peer-to-peer, offline-first voice communication app that converts speech to text, transmits it over **Wi-Fi Direct** or **Bluetooth**, and speaks it aloud on the receiving device — with optional real-time translation between **10 Indian languages**.

Think of it as a **walkie-talkie that speaks your language**, even when the person on the other end speaks a completely different one.

### The Pipeline

```
🎙️ Speaker                              🔊 Receiver
┌──────────┐    ┌────────────┐    ┌──────────────┐    ┌───────────┐
│ Voice In │───▶│ STT Engine │───▶│ Text Packet  │───▶│ Translate │───▶ TTS Out 🔊
│ (Hindi)  │    │ (Google)   │    │ (WiFi/BT)    │    │ (ML Kit)  │
└──────────┘    └────────────┘    └──────────────┘    └───────────┘
```

**Key Insight:** Instead of transmitting raw audio (which requires high bandwidth), iTantra transmits **text** — achieving a **~100× compression ratio** compared to PCM audio, making it viable for ultra-low-bandwidth channels.

---

## ✨ Features

### Core
- 🗣️ **Push-to-Talk (PTT)** — Hold-to-talk walkie-talkie UX
- 🧠 **Speech-to-Text** — Google's neural STT with 10 Indian language support
- 🔊 **Text-to-Speech** — Google Neural TTS with automatic high-quality voice selection
- 🌐 **Real-time Translation** — ML Kit on-device translation between all supported languages
- 📡 **Wi-Fi Direct P2P** — No internet, no router, no infrastructure needed
- 📶 **Bluetooth RFCOMM** — Alternative transport for BT-only scenarios
- 🔒 **Packet Integrity** — CRC32 checksums on every transmitted packet

### Languages (10 ISRO PS-Specified + 1 Bonus)
| # | Language | Code | Native Script |
|---|----------|------|---------------|
| 1 | Hindi | `hi-IN` | हिन्दी |
| 2 | English | `en-IN` | English |
| 3 | Tamil | `ta-IN` | தமிழ் |
| 4 | Gujarati | `gu-IN` | ગુજરાતી |
| 5 | Marathi | `mr-IN` | मराठी |
| 6 | Kannada | `kn-IN` | ಕನ್ನಡ |
| 7 | Malayalam | `ml-IN` | മലയാളം |
| 8 | Telugu | `te-IN` | తెలుగు |
| 9 | Odia | `or-IN` | ଓଡ଼ିଆ |
| 10 | Bengali | `bn-IN` | বাংলা |
| 11 | Assamese *(bonus)* | `as-IN` | অসমীয়া |

### Advanced
- 🚨 **Emergency Alerts** — Priority DANGER/WARNING messages bypass DND, max volume, SOS vibration
- 📊 **Pipeline Metrics Dashboard** — Real-time latency display (STT → Encode → Network → Translate → TTS)
- 🎵 **Audio Feedback** — Roger beeps and chimes for send/receive events
- 🗣️ **Voice Assistant Mode** — Fully eyes-free, audio-only operation
- 📉 **Bandwidth Throttle Simulator** — Proves viability on LoRa/UHF-like low-bitrate channels
- 🧹 **Automatic Cache Management** — Self-cleaning audio temp files
- 📜 **Message History** — Room-backed persistent message log with timestamps

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Presentation Layer                     │
│  HomeScreen · CommunicationScreen · SettingsScreen · History│
│  (Jetpack Compose + Material 3)                             │
├─────────────────────────────────────────────────────────────┤
│                       Domain Layer                          │
│  SendMessageUseCase · ReceiveMessageUseCase                 │
│  VoiceAssistantUseCase · SttBenchmark · DeviceProfiler      │
│  WerCalculator · ManageCacheUseCase                         │
├─────────────────────────────────────────────────────────────┤
│                        Data Layer                           │
│  MessageRepository · SettingsRepository (DataStore)         │
│  PacketSerializer (JSON + CRC32) · ITantraPacket (Binary)   │
├────────────────────────┬────────────────────────────────────┤
│    Speech Engines      │      Transport Layer               │
│  SpeechRecognitionMgr  │  WifiDirectTransport               │
│  AndroidNativeTtsEngine│  BluetoothRfcommTransport           │
│  SherpaOnnxSttEngine   │  BandwidthThrottledTransport        │
│  EnergyVadEngine       │  P2pSocketManager                   │
└────────────────────────┴────────────────────────────────────┘
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (Dagger) |
| Database | Room |
| Preferences | DataStore |
| STT | Android SpeechRecognizer (Google) |
| TTS | Android TextToSpeech (Google Neural) |
| Translation | Google ML Kit (on-device) |
| VAD | Energy-based VAD + Silero ONNX |
| Networking | Wi-Fi Direct + Bluetooth RFCOMM |
| Build | Gradle 8.7 + Version Catalogs |
| Min SDK | 34 (Android 14) |
| Target SDK | 35 (Android 15) |

---

## 📦 Installation

### Pre-built APK

Download the latest APK from the [**Releases**](https://github.com/ammenish/iTantra/releases) page.

> **Requirements:** Android 14+ (API 34), Google TTS installed, ~160 MB storage

### Build from Source

```bash
# Clone
git clone https://github.com/ammenish/iTantra.git
cd iTantra

# Create local.properties with your SDK path
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# Build debug APK
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

**Build Requirements:**
- JDK 17 or 21
- Android SDK with API 35
- ~4 GB RAM for Gradle

---

## 🚀 Quick Start

1. **Install** the APK on two Android devices
2. **Grant permissions** — Microphone, Location (for WiFi Direct), Bluetooth
3. **Set your language** in Settings (e.g., Hindi on Device A, Tamil on Device B)
4. **Connect** — Go to Connection tab → Discover → Tap a peer
5. **Talk** — Hold the PTT button, speak, release
6. **Listen** — The other device automatically translates and speaks your message

---

## 📁 Project Structure

```
iTantra/
├── app/
│   ├── libs/
│   │   └── sherpa-onnx.aar          # Sherpa ONNX runtime (VAD/STT models)
│   ├── src/main/
│   │   ├── assets/
│   │   │   ├── silero_vad.onnx      # Voice Activity Detection model
│   │   │   ├── benchmark/           # STT benchmark test data
│   │   │   └── models/              # On-device ML models
│   │   ├── java/com/mirage/itantra/
│   │   │   ├── app/                 # Application, MainActivity, CrashActivity
│   │   │   ├── communication/       # Transport abstraction layer
│   │   │   ├── data/                # Repository impl, DB, packet encoding
│   │   │   ├── di/                  # Hilt dependency injection modules
│   │   │   ├── domain/              # Use cases, models, interfaces
│   │   │   ├── service/             # Foreground service for P2P
│   │   │   ├── speech/              # STT & TTS engines
│   │   │   └── ui/                  # Compose screens & theme
│   │   ├── res/                     # Resources, strings, mipmaps
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml           # Version catalog
├── docs/                            # Documentation
├── build.gradle.kts                 # Root build script
├── settings.gradle.kts
└── gradle.properties
```

---

## 📊 Performance Characteristics

| Metric | Value |
|--------|-------|
| APK Size | ~156 MB (includes Sherpa ONNX native libs) |
| STT Latency | ~800ms – 2s (language-dependent) |
| TTS Latency | ~200ms – 500ms |
| Packet Encode | < 5ms |
| Network Transit (WiFi Direct) | < 50ms |
| End-to-End Pipeline | ~1.5s – 3.5s |
| Text Payload per Message | ~50–200 bytes |
| Compression vs Raw Audio | ~100× smaller |

---

## 🛡️ Offline Capabilities

iTantra is designed for **infrastructure-denied environments**:

- ✅ **STT** — Fully offline via Google's on-device speech pack (downloaded once)
- ✅ **TTS** — Fully offline via Google's neural voices (downloaded once)
- ✅ **Transport** — Wi-Fi Direct / Bluetooth — zero internet
- ⚠️ **Translation** — ML Kit requires a one-time model download (~30 MB per language pair), then works fully offline
- ✅ **Core Pipeline** — STT → Encode → Transmit → Decode → TTS works 100% offline

> **Note:** First launch requires a brief internet connection to download Google TTS/STT language packs and ML Kit translation models. After that, the app works entirely offline.

---

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 👥 Team

**Team Quantum Vision** — Built for the ISRO Problem Statement on offline multilingual communication systems.

---

<p align="center">
  <b>iTantra</b> — Breaking language barriers, one transmission at a time. 📡
</p>
