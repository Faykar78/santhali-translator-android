#!/bin/bash
# Run this script ONCE before building the app in Android Studio.
# It copies the downloaded AI models into the Android project's assets folder.

set -e
MODELS_SRC="/home/harsha/Downloads/santhali-models"
ASSETS_DEST="./app/src/main/assets/models"

echo "=== Santhali Translator — Model Setup ==="

if [ ! -d "$MODELS_SRC" ]; then
  echo "❌ Models not found at $MODELS_SRC"
  echo "   Please run the download script first."
  exit 1
fi

mkdir -p "$ASSETS_DEST/hindi-asr"
mkdir -p "$ASSETS_DEST/santhali-asr"
mkdir -p "$ASSETS_DEST/translation"

echo "Copying Hindi ASR model (~188 MB)..."
cp "$MODELS_SRC/hindi-asr/model.int8.onnx" "$ASSETS_DEST/hindi-asr/"
cp "$MODELS_SRC/hindi-asr/tokens.txt"      "$ASSETS_DEST/hindi-asr/"
cp "$MODELS_SRC/hindi-asr/config.json"     "$ASSETS_DEST/hindi-asr/"
echo "✅ Hindi ASR"

echo "Copying Santhali ASR (shared multilingual model)..."
cp "$MODELS_SRC/hindi-asr/model.int8.onnx" "$ASSETS_DEST/santhali-asr/"
cp "$MODELS_SRC/hindi-asr/tokens.txt"      "$ASSETS_DEST/santhali-asr/"
echo "✅ Santhali ASR"

echo "Copying IndicTrans2 translation model (~371 MB)..."
for f in encoder_model.onnx encoder_model.onnx.data \
          decoder_model.onnx decoder_shared.onnx.data \
          decoder_with_past_model.onnx \
          config.json model.SRC model.TGT \
          tokenizer_src.json tokenizer_tgt.json \
          dict.SRC.json dict.TGT.json; do
  cp "$MODELS_SRC/translation/$f" "$ASSETS_DEST/translation/" 2>/dev/null && echo "  $f ✅" || echo "  $f ⚠️ (skip)"
done
echo "✅ Translation model"

echo ""
echo "=== Setup Complete! Total assets size: ==="
du -sh "$ASSETS_DEST"
echo ""
echo "Now open this project in Android Studio and click Run ▶"
