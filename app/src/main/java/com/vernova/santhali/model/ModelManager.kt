package com.vernova.santhali.model

import android.content.Context
import android.util.Log
import com.vernova.santhali.asr.AsrEngine
import com.vernova.santhali.translation.IndicTrans2Translator
import com.vernova.santhali.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Orchestrates sequential model loading to stay within 1.5 GB RAM.
 *
 * Strategy (from architecture diagram):
 *  1. Load ONLY the required direction's ASR model
 *  2. Run ASR → get text
 *  3. Unload ASR, load Translation model
 *  4. Run Translation → get translated text
 *  5. Unload Translation, use built-in TTS (always resident)
 *
 * Peak RAM usage: ~700 MB – 1.3 GB (well under 2 GB tablet limit)
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"

        // Paths under app's internal storage (copied from APK assets on first run)
        const val HINDI_ASR_DIR     = "models/hindi-asr"
        const val SANTHALI_ASR_DIR  = "models/santhali-asr"
        const val TRANSLATION_DIR   = "models/translation"

        // Corresponding paths inside APK assets folder
        private const val ASSETS_HINDI_ASR    = "models/hindi-asr"
        private const val ASSETS_SANTHALI_ASR = "models/santhali-asr"
        private const val ASSETS_TRANSLATION  = "models/translation"
    }

    val asr         = AsrEngine(context)
    val translator  = IndicTrans2Translator(context)
    val tts         = TtsEngine(context)

    private val modelsRoot: File
        get() = File(context.filesDir, "models")

    /**
     * Check if all model files are present in internal storage.
     * On first run, the models are automatically copied from APK assets.
     */
    fun areModelsReady(): Boolean {
        val hindiAsr     = File(context.filesDir, "$HINDI_ASR_DIR/model.int8.onnx")
        val santhaliAsr  = File(context.filesDir, "$SANTHALI_ASR_DIR/tokens.txt")
        val translation  = File(context.filesDir, "$TRANSLATION_DIR/encoder_model.onnx")
        val ready = hindiAsr.exists() && santhaliAsr.exists() && translation.exists()
        Log.i(TAG, "Models ready check: $ready")
        Log.d(TAG, "  hindi-asr/model.int8.onnx: ${hindiAsr.exists()} (${hindiAsr.absolutePath})")
        Log.d(TAG, "  santhali-asr/tokens.txt: ${santhaliAsr.exists()}")
        Log.d(TAG, "  translation/encoder_model.onnx: ${translation.exists()}")
        return ready
    }

    /**
     * Copy models from APK assets/ into app's internal storage.
     * Only needed once on first launch. Large files (~500MB total) are
     * streamed to avoid OOM.
     */
    suspend fun copyModelsFromAssets() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Copying models from APK assets to ${context.filesDir}")

        copyAssetDir(ASSETS_HINDI_ASR, File(context.filesDir, HINDI_ASR_DIR))
        copyAssetDir(ASSETS_SANTHALI_ASR, File(context.filesDir, SANTHALI_ASR_DIR))
        copyAssetDir(ASSETS_TRANSLATION, File(context.filesDir, TRANSLATION_DIR))

        Log.i(TAG, "Asset copy complete")
    }

    /**
     * Copy models from an external directory (e.g. Downloads/santhali-models)
     * into app's internal storage. Alternative to asset copy.
     */
    suspend fun copyModelsFromExternal(externalModelsDir: File) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Copying models from ${externalModelsDir.absolutePath}")

        copyDir(
            src  = File(externalModelsDir, "hindi-asr"),
            dest = File(context.filesDir, HINDI_ASR_DIR)
        )
        copyDir(
            src  = File(externalModelsDir, "santhali-asr"),
            dest = File(context.filesDir, SANTHALI_ASR_DIR)
        )
        copyDir(
            src  = File(externalModelsDir, "translation"),
            dest = File(context.filesDir, TRANSLATION_DIR)
        )

        Log.i(TAG, "Model copy complete")
    }

    /** Initialize TTS (always-on, low RAM). */
    suspend fun initTts() = tts.initialize()

    /**
     * Load ASR for the given language direction.
     * Unloads any previously loaded ASR.
     */
    suspend fun loadAsr(lang: String) {
        Log.i(TAG, "Loading ASR for lang=$lang")
        // Both Hindi and Santhali use the same multilingual model
        val asrDir = File(context.filesDir, HINDI_ASR_DIR)
        asr.load(asrDir, lang)
    }

    /** Load the translation model (high RAM — release when done). */
    suspend fun loadTranslator() {
        Log.i(TAG, "Loading IndicTrans2 translation model")
        val transDir = File(context.filesDir, TRANSLATION_DIR)
        translator.load(transDir)
    }

    /** Release all engines and free RAM. */
    fun releaseAll() {
        asr.release()
        translator.release()
        tts.release()
        Log.i(TAG, "All models released")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Copy all files from an APK asset directory to a local directory.
     * Uses streaming to handle large ONNX files safely.
     */
    private fun copyAssetDir(assetPath: String, destDir: File) {
        destDir.mkdirs()
        val assets = context.assets.list(assetPath) ?: return
        Log.d(TAG, "Copying asset dir '$assetPath' (${assets.size} files) → ${destDir.absolutePath}")

        for (fileName in assets) {
            val target = File(destDir, fileName)
            val assetFilePath = "$assetPath/$fileName"

            // Check if this is a subdirectory
            val subFiles = context.assets.list(assetFilePath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                // Recurse into subdirectory
                copyAssetDir(assetFilePath, target)
                continue
            }

            if (target.exists()) {
                Log.d(TAG, "  Skip $fileName — already exists (${target.length() / 1024} KB)")
                continue
            }

            // Stream copy to avoid loading entire file into RAM
            context.assets.open(assetFilePath).use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var totalBytes = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalBytes += read
                    }
                    Log.d(TAG, "  Copied $fileName (${totalBytes / 1024} KB)")
                }
            }
        }
    }

    private fun copyDir(src: File, dest: File) {
        if (!src.exists()) { Log.w(TAG, "Source dir not found: $src"); return }
        dest.mkdirs()
        src.listFiles()?.forEach { file ->
            if (file.isFile) {
                val target = File(dest, file.name)
                if (!target.exists() || target.length() != file.length()) {
                    file.copyTo(target, overwrite = true)
                    Log.d(TAG, "Copied ${file.name} (${file.length() / 1024} KB)")
                } else {
                    Log.d(TAG, "Skip ${file.name} — already exists")
                }
            }
        }
    }
}
