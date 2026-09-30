# iTantra Implementation Plan

This document outlines the systematic execution of the iTantra architecture, tracing the path from the initial gap analysis to the final production-ready state.

## Phase 1: Core Foundation & Gap Remediation (Completed)

This phase focused on restructuring the prototype to honestly address the 10 gaps identified against the ISRO Problem Statement (PS).

- **10-Language Compliance:** Upgraded STT integration to report and test all 10 PS-specified Indian languages (`SherpaOnnxSttEngine`, `Language`).
- **Honest VAD Integration:** Replaced the misleadingly named `SileroVadEngine` with `EnergyVadEngine`, an honest RMS-based Voice Activity Detector with adaptive noise floors.
- **Pipeline Metric Instrumentation:** Built `PipelineMetrics` and instrumented both sender and receiver use cases to measure exact STT, translation, encoding, and TTS latency (`SendMessageUseCase`, `ReceiveMessageUseCase`).
- **Bandwidth Benchmark Simulation:** Created `BandwidthThrottledTransport` to simulate LoRa/UHF throughput limits, proving semantic compression speedup mathematically.
- **Accuracy Test Harness:** Built `SttBenchmark` to calculate Word Error Rate (WER) using Levenshtein distance against reference audio/text pairs.
- **Efficiency Profiler:** Built `DeviceProfiler` to capture APK size, heap size, active CPU time, and total RAM footprint.
- **Alert Playback Hardening:** Ensured alerts use Android's `STREAM_ALARM` with `USAGE_ALARM` audio attributes and vibration to bypass DND and screen locks.
- **Transport Abstraction (Bluetooth):** Added `BluetoothRfcommTransport` to prove transport independence.
- **Packet Integrity:** Added CRC32 checksums to `PacketSerializer` for basic corruption detection.

## Phase 2: Final UI Polish & Validation (In Progress)

This phase focuses on the "wow factor" and formal testing.

- [x] UI Micro-animations: Added PTT pulse animations, glassmorphism borders, and entry transitions.
- [x] Metrics Dashboard: Built an interactive, collapsible pipeline latency dashboard.
- [ ] **Pending (Manual):** Compile the app on a mid-range Android test device (e.g., Redmi Note).
- [ ] **Pending (Manual):** Run the `SttBenchmark` on the device and populate the 10-language validation matrix with actual WER and RTF numbers.

## Phase 3: The Embedded Bridge (Future)

To achieve the ultimate goal of low-bitrate radio transmission, iTantra must bridge from the smartphone to an embedded radio module (e.g., ESP32 + LoRa SX1276).

- **Serial Transport:** Implement `UsbSerialTransport` (via OTG) or extend `BluetoothRfcommTransport` to talk to an ESP32.
- **Embedded Firmware:** Write C++ firmware for the ESP32 that receives the iTantra packet via serial/BT and transmits it over the LoRa modem.
- **Binary ITantraPacket:** Replace the current JSON payload in `PacketSerializer` with the fully packed, bit-level `ITantraPacket` format (1 byte header + 4 byte CRC + N byte compressed text).

## Phase 4: Full Offline Guarantee (Future)

To satisfy the strictest interpretation of "offline", we must remove the initial internet download requirement of Google ML Kit.

- **NLLB/IndicTrans2 Integration:** Bundle a quantised NLLB ONNX model or an IndicTrans2 GGUF model directly into the APK.
- **Phase 7 TTS Integration:** Finalize the Piper/VITS text-to-speech integration to replace Android's native TTS, ensuring consistent voices across all devices without relying on Google TTS engine updates.
