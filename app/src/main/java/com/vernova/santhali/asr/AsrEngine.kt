package com.vernova.santhali.asr

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ASR engine using sherpa-onnx v1.13.8 + IndicConformer CTC model.
 *
 * Note: In sherpa-onnx v1.13.8, the IndicConformer model (NeMo-style CTC)
 * uses OfflineNemoEncDecCtcModelConfig (via the `nemoCtc` field).
 * The old `OfflineCtcModelConfig` no longer exists in this version.
 */
class AsrEngine(private val context: Context) {

    companion object {
        private const val TAG = "AsrEngine"
        const val LANG_HINDI    = "hi"
        const val LANG_SANTHALI = "sat"
    }

    private var recognizer: OfflineRecognizer? = null
    private var currentLang: String? = null

    /**
     * Load ASR model for [lang].
     * Both Hindi and Santhali share the same multilingual IndicConformer model.
     */
    suspend fun load(modelDir: File, lang: String) = withContext(Dispatchers.IO) {
        if (currentLang == lang && recognizer != null) return@withContext
        release()

        Log.i(TAG, "Loading ASR for lang=$lang from ${modelDir.absolutePath}")

        val modelPath  = File(modelDir, "model.int8.onnx").absolutePath
        val tokensPath = File(modelDir, "tokens.txt").absolutePath

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = 16000,
                featureDim = 80
            ),
            modelConfig = OfflineModelConfig(
                // IndicConformer is a NeMo CTC-style model → use `nemo` field
                nemo = OfflineNemoEncDecCtcModelConfig(model = modelPath),
                tokens      = tokensPath,
                numThreads  = 2,
                debug       = false,
                provider    = "cpu"
            ),
            decodingMethod = "greedy_search"
        )

        // assetManager = null → we're using absolute file paths, not Android assets
        recognizer  = OfflineRecognizer(assetManager = null, config = config)
        currentLang = lang
        Log.i(TAG, "ASR ready for lang=$lang")
    }

    /** Transcribe a 16kHz mono WAV file. */
    suspend fun transcribe(wavFile: File): String = withContext(Dispatchers.Default) {
        val rec = recognizer ?: error("ASR engine not loaded. Call load() first.")
        Log.d(TAG, "Transcribing ${wavFile.name} (${wavFile.length() / 1024} KB)")

        val stream: OfflineStream = rec.createStream()
        val samples = readWavSamples(wavFile)
        stream.acceptWaveform(samples, sampleRate = 16000)
        rec.decode(stream)

        val result = rec.getResult(stream).text.trim()
        stream.release()
        Log.d(TAG, "ASR [$currentLang]: \"$result\"")
        result
    }

    /** Transcribe raw PCM float samples directly (no file I/O). */
    suspend fun transcribeSamples(samples: FloatArray): String = withContext(Dispatchers.Default) {
        val rec = recognizer ?: error("ASR engine not loaded.")
        val stream: OfflineStream = rec.createStream()
        stream.acceptWaveform(samples, sampleRate = 16000)
        rec.decode(stream)
        val result = rec.getResult(stream).text.trim()
        stream.release()
        result
    }

    fun release() {
        recognizer?.release()
        recognizer = null
        currentLang = null
        Log.i(TAG, "ASR released")
    }

    private fun readWavSamples(wav: File): FloatArray {
        val bytes = wav.readBytes()
        val headerSize = 44
        val numSamples = (bytes.size - headerSize) / 2
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val lo  = bytes[headerSize + i * 2].toInt() and 0xFF
            val hi  = bytes[headerSize + i * 2 + 1].toInt()
            val pcm = (hi shl 8 or lo).toShort()
            samples[i] = pcm / 32768.0f
        }
        return samples
    }
}
