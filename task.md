# Current Project Tasks

## ✅ Completed
- `[x]` Initial project structure and setup for `iTantra`.
- `[x]` Basic UI components (HomeScreen, SettingsScreen, MessageBubble).
- `[x]` Data layer setup (MessageRepository, PacketSerializer).
- `[x]` P0: Update STT `availableLanguages()` to report all 10 PS languages.
- `[x]` P0: Rename SileroVadEngine → EnergyVadEngine (honesty + improved VAD).
- `[x]` P0: Create PipelineMetrics data model for granular timing.
- `[x]` P0: Instrument SendMessageUseCase with per-stage latency.
- `[x]` P0: Instrument ReceiveMessageUseCase with translation + TTS timing.
- `[x]` P0: Update HomeViewModel to expose full PipelineMetrics.
- `[x]` P0: Update Language.kt (isPsSpecified, psSpecifiedLanguages()).
- `[x]` P1: Add BandwidthThrottledTransport for low-bitrate demonstration.
- `[x]` P0: Create STT test harness with pre-recorded audio, measure WER per language.
- `[x]` P0: Measure APK size, model sizes, peak RAM, idle CPU.
- `[x]` P1: Validate alert playback (DND, locked screen, during calls).
- `[x]` P1: Add Bluetooth RFCOMM transport stub.
- `[x]` P1: Document ML Kit offline readiness (initial download required).
- `[x]` P2: Packet CRC validation demo.
- `[x]` HomeScreen: Format the timestamp nicely.
- `[x]` MessageRepositoryImpl: Review and complete the `MessageEntity.toDomain()` mapping logic.

- `[x]` P2: UI polish and animations.
- `[x]` Create a formal `implementation_plan.md` for the current phase execution plan.
- `[x]` **P0**: **New App (`itantrav3`)**: Configured independent application package `com.mirage.itantrav3.debug` ("iTantra v3") so the existing app remains intact.
- `[x]` **P0**: **High-Accuracy Multilingual STT**: Fixed Android SpeechRecognizer to use Google system engine with BCP-47 locale tags (`hi-IN`, `mr-IN`, `ta-IN`, `te-IN`, `gu-IN`, `bn-IN`, `kn-IN`, `ml-IN`, `or-IN`, `en-IN`), candidate selection using confidence scores (`EXTRA_CONFIDENCE_SCORES`), speech normalization, and walkie-talkie phrasing silence timeouts.
- `[x]` **P0**: **High-Accuracy Google Neural TTS**: Explicitly bound TTS to Google TTS engine (`com.google.android.tts`) with automatic high-quality neural voice selection (`tts.voices`), Devanagari Danda (`।`) pause inflection, and `QUEUE_FLUSH` for instant speech.
- `[x]` **P0**: **Receiver Seamless Audio Playback**: Ensured incoming transmissions always trigger loudspeaker TTS even if translation fails or is offline, using translation fallback.
- `[x]` **P0**: **Receiver Audio Routing**: Removed local sender TTS playback so only the receiving device translates and speaks incoming audio over loudspeaker with sequenced radio alert chimes. Preserved persistent P2P background transport.

## 🔲 Pending
- `[ ]` **P0**: Test on mid-range device (Redmi Note series or similar).
- `[ ]` **P0**: Build full 10-language validation matrix with real test results.
- `[x]` **P1**: **Cache Management:** Implement automatic removal of processed audio files and temporary data to maintain a tiny storage footprint.
- `[x]` **P1**: **Walkie-Talkie UX:** Add audio feedback (roger beeps/chimes) for 'Message Sent' and 'Message Received' events.
- `[x]` **P1**: **Alert Tiers:** Introduce priority levels for messages (e.g., Info, Warning, Danger) to conditionally trigger the max-volume/wake-lock overrides only for critical alerts.
- `[x]` **P2**: **Voice Assistant Mode:** Build a fully audio-based, eyes-free mode with voice prompts ("Connected", "Incoming message", etc.) for hands-free operation.
