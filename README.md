# 🗣️ Santhali Translator — Offline Hindi ↔ Santhali for Android

An **offline** Hindi ↔ Santhali translator Android app powered by AI models running entirely on-device. No internet connection required after setup.

## ✨ Features

- **Offline Translation** — Hindi → Santhali and Santhali → Hindi using IndicTrans2
- **Speech Recognition (ASR)** — Speak in Hindi or Santhali and get text via Sherpa-ONNX
- **Text-to-Speech (TTS)** — Listen to translations spoken aloud
- **Jetpack Compose UI** — Modern Material 3 interface
- **KV-Cache Decoding** — Optimized autoregressive decoder for faster inference on mobile
- **Android 9+ (API 28)** — Supports tablets and phones

## 🏗️ Architecture

| Component | Model | Size | Source |
|---|---|---|---|
| **Translation** | IndicTrans2 Indic-Indic 320M (ONNX int8) | ~371 MB | [HuggingFace](https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8) |
| **Hindi ASR** | IndicConformer CTC-RNNT Large (int8) | ~188 MB | [HuggingFace](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-hin-conformer-large) |
| **Santhali ASR** | IndicConformer (shared multilingual) | ~188 MB | Same as Hindi ASR |
| **Runtime** | ONNX Runtime 1.28.0 + Sherpa-ONNX | — | Bundled |

**Total model size: ~747 MB**

---

## 📥 Setup Guide

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17** (bundled with Android Studio)
- **Android SDK 34+** with Build Tools
- **Git** and **Git LFS** (optional, for large files)
- ~2 GB free disk space (models + build artifacts)

### Step 1: Clone the Repository

```bash
git clone https://github.com/Faykar78/santhali-translator-android.git
cd santhali-translator-android
```

### Step 2: Download AI Models

The AI models are too large for GitHub (~747 MB total). Download them from HuggingFace using the links below.

#### Option A: Automated Download (Recommended)

Run the download script:

```bash
# Install huggingface-cli if not already installed
pip install huggingface-hub

# Create models directory
mkdir -p ~/Downloads/santhali-models/hindi-asr
mkdir -p ~/Downloads/santhali-models/translation
```

**Download Hindi ASR model (~188 MB):**

```bash
cd ~/Downloads/santhali-models/hindi-asr

# Download from sherpa-onnx IndicConformer
wget https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-hin-conformer-large/resolve/main/model.int8.onnx
wget https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-hin-conformer-large/resolve/main/tokens.txt
```

**Download IndicTrans2 Translation model (~371 MB):**

```bash
cd ~/Downloads/santhali-models/translation

# Encoder
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/encoder_model.onnx
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/encoder_model.onnx.data

# Decoder (with shared weights)
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/decoder_model.onnx
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/decoder_shared.onnx.data
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/decoder_with_past_model.onnx

# Tokenizers
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/tokenizer_src.json
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/tokenizer_tgt.json

# Vocab / Config
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/config.json
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/dict.SRC.json
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/dict.TGT.json
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/model.SRC
wget https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8/resolve/main/model.TGT
```

#### Option B: Download via HuggingFace Web UI

1. **Hindi ASR**: Go to [csukuangfj/sherpa-onnx-nemo-ctc-hin-conformer-large](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-hin-conformer-large) → Click "Files and versions" → Download `model.int8.onnx` and `tokens.txt`

2. **Translation**: Go to [hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8](https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8) → Click "Files and versions" → Download all the files listed in the table below

### Step 3: Place Models in the Project

Copy the downloaded models into the app's `assets/models/` directory:

```bash
cd santhali-translator-android
chmod +x setup_models.sh
./setup_models.sh
```

Or manually copy them:

```
app/src/main/assets/models/
├── hindi-asr/
│   ├── config.json                    (4 KB)
│   ├── model.int8.onnx                (188 MB)  ← ASR model
│   └── tokens.txt                     (72 KB)
├── santhali-asr/
│   ├── model.int8.onnx                (188 MB)  ← Same as Hindi ASR
│   └── tokens.txt                     (72 KB)
└── translation/
    ├── config.json                    (4 KB)
    ├── decoder_model.onnx             (2 MB)
    ├── decoder_shared.onnx.data       (194 MB)  ← Shared decoder weights
    ├── decoder_with_past_model.onnx   (1.8 MB)
    ├── dict.SRC.json                  (3.3 MB)
    ├── dict.TGT.json                  (3.3 MB)
    ├── encoder_model.onnx             (812 KB)
    ├── encoder_model.onnx.data        (115 MB)  ← Encoder weights
    ├── model.SRC                      (3.2 MB)
    ├── model.TGT                      (3.2 MB)
    ├── tokenizer_src.json             (23 MB)   ← BPE tokenizer (source)
    └── tokenizer_tgt.json             (23 MB)   ← BPE tokenizer (target)
```

> **Note:** The Santhali ASR uses the same multilingual IndicConformer model as Hindi ASR. Copy the same `model.int8.onnx` and `tokens.txt` into both directories.

### Step 4: Verify Model Files

Run this command to verify all files are in place:

```bash
du -sh app/src/main/assets/models/
# Expected output: ~747M
```

---

## 🚀 Running in Android Studio

### Step 1: Open the Project

1. Open **Android Studio**
2. Click **File → Open**
3. Navigate to the `santhali-translator-android` folder and click **OK**
4. Wait for Gradle sync to complete (first time may take 2-3 minutes)

### Step 2: Configure an Emulator or Device

#### Using an Emulator (API 28+)
1. Go to **Tools → Device Manager → Create Virtual Device**
2. Select a **tablet** (e.g., Pixel Tablet) or **phone** profile
3. Select a system image with **API 28** (Android 9 Pie) or higher
4. **Recommended ABI**: `x86_64` for best emulator performance
5. Set **RAM to at least 4 GB** (the models are memory-intensive)

#### Using a Physical Device
1. Enable **Developer Options** on your Android device
2. Enable **USB Debugging**
3. Connect via USB and authorize the computer
4. The device must be **Android 9 (API 28)** or higher

### Step 3: Build and Run

1. Select your target device from the device dropdown in the toolbar
2. Click **▶ Run** (or press `Shift + F10`)
3. Wait for the build to complete (~1-2 minutes for first build)
4. The app will install and launch on your device

> **⚠️ First Launch:** The app copies ~747 MB of model files from the APK to internal storage on first run. This takes **1-3 minutes** depending on device speed. You'll see a loading indicator.

### Step 4: Use the App

1. **Type** Hindi or Santhali text in the input field
2. **Tap the translate button** to translate
3. **Tap the microphone** to use speech-to-text (requires microphone permission)
4. **Swap languages** using the swap button between Hindi ↔ Santhali

---

## ⚙️ Build Configuration

| Setting | Value |
|---|---|
| `minSdk` | 28 (Android 9 Pie) |
| `targetSdk` | 34 (Android 14) |
| `compileSdk` | 37 |
| Supported ABIs | `arm64-v8a`, `x86_64`, `x86` |
| Kotlin | 2.1.20 |
| Compose BOM | 2026.02.01 |
| ONNX Runtime | 1.28.0 |

---

## 📁 Project Structure

```
santhali-translator-android/
├── app/
│   ├── libs/
│   │   └── sherpa-onnx.aar              # Sherpa-ONNX local AAR for ASR
│   ├── src/main/
│   │   ├── assets/models/               # AI models (not in Git)
│   │   ├── java/com/vernova/santhali/
│   │   │   ├── MainActivity.kt          # Single-activity entry point
│   │   │   ├── TranslatorApplication.kt # App-level init
│   │   │   ├── asr/
│   │   │   │   └── AsrEngine.kt         # Speech recognition engine
│   │   │   ├── audio/
│   │   │   │   └── AudioRecorder.kt     # Microphone capture
│   │   │   ├── model/
│   │   │   │   └── ModelManager.kt      # Model file management
│   │   │   ├── translation/
│   │   │   │   ├── IndicTrans2Translator.kt  # ONNX translation engine
│   │   │   │   └── SentencePieceTokenizer.kt # BPE tokenizer
│   │   │   ├── tts/
│   │   │   │   └── TtsEngine.kt         # Text-to-speech
│   │   │   ├── ui/
│   │   │   │   ├── screens/
│   │   │   │   │   └── TranslatorScreen.kt   # Main UI
│   │   │   │   └── theme/               # Material 3 theme
│   │   │   └── viewmodel/
│   │   │       └── TranslatorViewModel.kt    # UI state management
│   │   ├── res/                         # Resources, icons, themes
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml               # Dependency versions
├── setup_models.sh                      # Model setup script
└── README.md
```

---

## 🔧 Troubleshooting

### "Models not found" on launch
- Ensure all model files are placed in `app/src/main/assets/models/` with the correct directory structure
- Clean and rebuild: **Build → Clean Project**, then **Build → Rebuild Project**

### Build error: `Cannot build selected target ABI: x86`
- The project supports `arm64-v8a`, `x86_64`, and `x86` ABIs
- Ensure your emulator uses one of these ABIs

### `UnsatisfiedLinkError: OrtGetApiBase`
- Run **Build → Clean Project** then rebuild
- This happens when stale native libraries are cached

### Translation is very slow on emulator
- The 320M parameter model is CPU-intensive. On an emulator, expect **10-30 seconds per token**
- For faster testing, use a **physical ARM64 device** (3-5x faster)
- The KV-cache decoder makes each step after the first significantly faster

### Out of memory / app crashes
- Increase emulator RAM to **4 GB** minimum
- Close other apps on physical devices
- The translation model uses ~350-600 MB RAM at runtime

---

## 📄 Model Licenses

| Model | License | Authors |
|---|---|---|
| IndicTrans2 | [MIT](https://opensource.org/licenses/MIT) | [AI4Bharat](https://ai4bharat.iitm.ac.in/) |
| IndicConformer | [MIT](https://opensource.org/licenses/MIT) | [AI4Bharat](https://ai4bharat.iitm.ac.in/) |
| Sherpa-ONNX | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) | [k2-fsa](https://github.com/k2-fsa/sherpa-onnx) |

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📜 License

This project is open source. See the model licenses above for individual model usage terms.
