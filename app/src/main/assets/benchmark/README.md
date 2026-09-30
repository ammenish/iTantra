# iTantra STT Benchmark Test Data

## Directory Structure

Each language has its own directory matching the ISO 639-1 code:

```
benchmark/
├── hi/           # Hindi
│   ├── 01.wav    # Test audio (16kHz, 16-bit, mono PCM WAV)
│   ├── 01.txt    # Ground truth transcription (UTF-8)
│   ├── 02.wav
│   ├── 02.txt
│   └── ...
├── en/           # English
├── gu/           # Gujarati
├── mr/           # Marathi
├── kn/           # Kannada
├── ml/           # Malayalam
├── ta/           # Tamil
├── te/           # Telugu
├── or/           # Odia
└── bn/           # Bengali
```

## Audio Requirements

- **Format**: WAV (RIFF/WAVE)
- **Sample Rate**: 16000 Hz (16kHz)
- **Bit Depth**: 16-bit signed PCM
- **Channels**: Mono (1 channel)
- **Duration**: 2-10 seconds per sample (typical sentence length)

## Ground Truth Requirements

- **Encoding**: UTF-8
- **Content**: Exact transcription of the audio in the native script
- **Punctuation**: Include sentence-ending punctuation (।, ., !, ?)
- **No extra whitespace**: Trim leading/trailing spaces

## Recommended Test Sentences

For each language, include at least 10-20 test sentences covering:

1. **Emergency phrases**: "Help me", "Send ambulance", "Fire!"
2. **Short commands**: "Yes", "No", "Copy that"
3. **Location descriptions**: "I am at the river crossing"
4. **Status reports**: "The bridge is damaged, road blocked"
5. **Numbers**: "There are 5 people injured"
6. **Common conversation**: Standard greeting and response

## How to Record Test Audio

### Option 1: Record directly on device
```bash
# Record 5 seconds of audio at 16kHz mono
adb shell am start -a android.intent.action.MAIN -n com.android.soundrecorder/.SoundRecorder
```

### Option 2: Use ffmpeg to convert existing recordings
```bash
# Convert any audio to the required format
ffmpeg -i input.mp3 -ar 16000 -ac 1 -acodec pcm_s16le output.wav
```

### Option 3: Use the iTantra app itself
Record using the PTT button, then extract the audio data.

## Running the Benchmark

The benchmark runs from the Settings screen → "Run STT Benchmark" button,
or programmatically:

```kotlin
val benchmark = SttBenchmark(context, sttEngine)
val results = benchmark.runFullBenchmark()
Log.i("WER", results.toLogString())
Log.i("WER_TABLE", results.toMarkdownTable())
```
