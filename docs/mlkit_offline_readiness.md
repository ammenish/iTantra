# ML Kit Offline Readiness

iTantra uses Google's ML Kit for on-device translation to support the 10 ISRO PS-specified languages. 

## The Caveat: Initial Download

While ML Kit translation runs **100% offline** during operation, it requires an **initial internet connection** to download the language models (approx. 30MB per language pair).

If a user installs the app in an area with no internet connection (e.g., during a disaster) and tries to translate English to Hindi for the first time, it will fail because the model is not present on the device.

## How iTantra Addresses This

### 1. Pre-fetching Models on First Launch
In `MLKitTranslationEngine.kt`, the engine checks if models exist. If not, it requests a download. This should ideally happen when the app is first installed, assuming the user still has internet.

### 2. Honest Evaluation Status
In a true offline-first deployment (e.g., flashing the APK onto devices via USB in the field), the ML Kit approach has a gap. To achieve a 100% no-internet-ever requirement, the translation models would need to be bundled directly into the APK (like we do with the Sherpa-ONNX STT models).

### 3. Future Mitigation
For the final production version, we recommend replacing ML Kit with a bundled NLLB (No Language Left Behind) ONNX model or an IndicTrans2 GGUF model that is packaged within the APK, guaranteeing zero-day offline availability.
