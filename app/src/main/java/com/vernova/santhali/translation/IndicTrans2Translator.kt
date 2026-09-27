package com.vernova.santhali.translation

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * IndicTrans2 seq2seq translator using ONNX Runtime for Android.
 *
 * Decoding strategy:
 *   Step 0:  decoder_model.onnx       (full sequence → logits + KV cache)
 *   Step 1+: decoder_with_past_model  (last token + KV cache → logits + updated KV)
 */
@Suppress("UNCHECKED_CAST")
class IndicTrans2Translator(private val context: Context) {

    companion object {
        private const val TAG = "IndicTrans2"
        private const val MAX_SRC_LEN = 128
        private const val MAX_TGT_LEN = 48
        private const val NUM_LAYERS   = 18
        private const val NUM_HEADS    = 8
        private const val HEAD_DIM     = 64
        private const val TIMEOUT_MS   = 180_000L

        private val LANG_CODE = mapOf(
            "hi"  to "hin_Deva",
            "sat" to "sat_Olck",
            "en"  to "eng_Latn",
            "te"  to "tel_Telu",
            "ta"  to "tam_Taml",
            "kn"  to "kan_Knda",
            "ml"  to "mal_Mlym",
            "mr"  to "mar_Deva"
        )

        private val KV_TYPES = listOf("decoder.key", "decoder.value", "encoder.key", "encoder.value")
    }

    private var env: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var decoderWithPastSession: OrtSession? = null

    private var srcTokenizer: SentencePieceTokenizer? = null
    private var tgtTokenizer: SentencePieceTokenizer? = null

    private val bos = 0L
    private val eos = 2L

    suspend fun load(modelDir: File) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Loading IndicTrans2 from $modelDir")

        env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
            addCPU(true)
        }

        encoderSession = env!!.createSession(
            File(modelDir, "encoder_model.onnx").absolutePath, opts
        )
        decoderSession = env!!.createSession(
            File(modelDir, "decoder_model.onnx").absolutePath, opts
        )
        decoderWithPastSession = env!!.createSession(
            File(modelDir, "decoder_with_past_model.onnx").absolutePath, opts
        )

        srcTokenizer = SentencePieceTokenizer(File(modelDir, "tokenizer_src.json"))
        tgtTokenizer = SentencePieceTokenizer(File(modelDir, "tokenizer_tgt.json"))

        Log.i(TAG, "IndicTrans2 loaded — encoder + decoder + KV-cache decoder ready")
    }

    suspend fun translate(
        text: String,
        srcLang: String,
        tgtLang: String
    ): String = withContext(Dispatchers.Default) {

        val enc = encoderSession ?: error("Translator not loaded.")
        val srcTok = srcTokenizer ?: error("Source tokenizer not loaded.")
        val tgtTok = tgtTokenizer ?: error("Target tokenizer not loaded.")

        val tgtCode = LANG_CODE[tgtLang] ?: error("Unsupported target: $tgtLang")
        val srcText = "$tgtCode $text"
        Log.d(TAG, "Translating [$srcLang→$tgtLang]: \"$srcText\"")

        // Tokenize
        val srcIds = srcTok.encode(srcText).take(MAX_SRC_LEN).toLongArray()
        val seqLen = srcIds.size
        Log.d(TAG, "Source tokens ($seqLen): ${srcIds.take(10).toList()}...")

        // Encode
        val attMaskData = LongArray(seqLen) { 1L }
        val encInputs = mapOf(
            "input_ids"      to OnnxTensor.createTensor(env, LongBuffer.wrap(srcIds), longArrayOf(1, seqLen.toLong())),
            "attention_mask"  to OnnxTensor.createTensor(env, LongBuffer.wrap(attMaskData), longArrayOf(1, seqLen.toLong()))
        )

        Log.d(TAG, "Running encoder...")
        val t0 = System.currentTimeMillis()
        val encOut = enc.run(encInputs)
        Log.d(TAG, "Encoder done in ${System.currentTimeMillis() - t0}ms")

        val encHidden = encOut.get(0).value as Array<Array<FloatArray>>
        val hidDim = encHidden[0][0].size
        val encFlat = FloatArray(seqLen * hidDim)
        for (t in 0 until seqLen)
            for (d in 0 until hidDim)
                encFlat[t * hidDim + d] = encHidden[0][t][d]

        // Decode with KV cache
        val startTime = System.currentTimeMillis()
        val outputIds = kvCacheDecode(encFlat, seqLen, hidDim, attMaskData, startTime)
        Log.d(TAG, "Decode done: ${outputIds.size} tokens in ${System.currentTimeMillis() - startTime}ms")

        // Detokenize
        val result = tgtTok.decode(outputIds.map { it.toInt() })
        Log.i(TAG, "Translation: \"$result\"")

        encInputs.values.forEach { it.close() }
        encOut.close()
        result
    }

    private fun kvCacheDecode(
        encFlat: FloatArray,
        encLen: Int,
        hidDim: Int,
        attMask: LongArray,
        startTime: Long
    ): LongArray {

        val dec0 = decoderSession ?: error("Decoder not loaded.")
        val decN = decoderWithPastSession ?: error("Decoder with past not loaded.")
        val generatedIds = mutableListOf<Long>()

        // ── Step 0: decoder_model with [BOS] ────────────────────────────────
        Log.d(TAG, "Decode step 0 (initial)...")
        val step0Start = System.currentTimeMillis()

        val step0Inputs = mutableMapOf<String, OnnxTensor>(
            "input_ids" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(bos)), longArrayOf(1, 1)),
            "encoder_attention_mask" to OnnxTensor.createTensor(env, LongBuffer.wrap(attMask), longArrayOf(1, encLen.toLong())),
            "encoder_hidden_states" to OnnxTensor.createTensor(env, FloatBuffer.wrap(encFlat), longArrayOf(1, encLen.toLong(), hidDim.toLong()))
        )

        val step0Out = dec0.run(step0Inputs)
        Log.d(TAG, "Step 0 took ${System.currentTimeMillis() - step0Start}ms")

        // Get logits → argmax
        val logits0 = (step0Out.get(0) as OnnxTensor).value as Array<Array<FloatArray>>
        var nextToken = argmax(logits0[0][0])
        generatedIds.add(nextToken)
        Log.d(TAG, "Step 0 → token=$nextToken")

        // Extract KV cache
        var kvCache = extractKvCache(step0Out)

        step0Inputs.values.forEach { it.close() }
        step0Out.close()

        if (nextToken == eos) {
            freeKvCache(kvCache)
            return longArrayOf()
        }

        // ── Steps 1+: decoder_with_past_model ───────────────────────────────
        for (step in 1 until MAX_TGT_LEN) {
            if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                Log.w(TAG, "Decode timed out at step $step")
                break
            }

            val stepStart = System.currentTimeMillis()

            val stepInputs = mutableMapOf<String, OnnxTensor>(
                "input_ids" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(nextToken)), longArrayOf(1, 1)),
                "encoder_attention_mask" to OnnxTensor.createTensor(env, LongBuffer.wrap(attMask), longArrayOf(1, encLen.toLong()))
            )

            // Add 72 KV cache tensors
            for (layer in 0 until NUM_LAYERS) {
                for ((typeIdx, typeName) in KV_TYPES.withIndex()) {
                    stepInputs["past_key_values.$layer.$typeName"] = kvCache[layer * 4 + typeIdx]
                }
            }

            val stepOut = decN.run(stepInputs)
            Log.d(TAG, "Step $step took ${System.currentTimeMillis() - stepStart}ms")

            val logitsN = (stepOut.get(0) as OnnxTensor).value as Array<Array<FloatArray>>
            nextToken = argmax(logitsN[0][0])
            generatedIds.add(nextToken)
            Log.d(TAG, "Step $step → token=$nextToken")

            // Update KV cache
            val newKvCache = extractKvCache(stepOut)
            freeKvCache(kvCache)
            kvCache = newKvCache

            stepInputs["input_ids"]?.close()
            stepInputs["encoder_attention_mask"]?.close()
            stepOut.close()

            if (nextToken == eos) break
        }

        freeKvCache(kvCache)
        return generatedIds.takeWhile { it != eos }.toLongArray()
    }

    /**
     * Extract 72 KV cache tensors from decoder output.
     * Output order: logits (idx 0), then present.0.decoder.key (idx 1), etc.
     */
    private fun extractKvCache(result: OrtSession.Result): Array<OnnxTensor> {
        // Collect all outputs into a list for indexed access
        val allOutputs = mutableListOf<OnnxValue>()
        for (entry in result) {
            allOutputs.add(entry.value)
        }

        return Array(NUM_LAYERS * 4) { idx ->
            val outputIdx = 1 + idx  // skip logits at index 0
            val tensor = allOutputs[outputIdx] as OnnxTensor
            val shape = tensor.info.shape
            val data = tensor.getFloatBuffer()

            // Copy data (original freed when result is closed)
            val copy = FloatBuffer.allocate(data.remaining())
            copy.put(data)
            copy.flip()

            OnnxTensor.createTensor(env, copy, shape)
        }
    }

    private fun freeKvCache(cache: Array<OnnxTensor>) {
        for (t in cache) {
            try { t.close() } catch (_: Exception) {}
        }
    }

    private fun argmax(logits: FloatArray): Long {
        var maxIdx = 0
        var maxVal = logits[0]
        for (i in 1 until logits.size) {
            if (logits[i] > maxVal) {
                maxVal = logits[i]
                maxIdx = i
            }
        }
        return maxIdx.toLong()
    }

    fun release() {
        encoderSession?.close()
        decoderSession?.close()
        decoderWithPastSession?.close()
        env?.close()
        encoderSession = null
        decoderSession = null
        decoderWithPastSession = null
        env = null
        srcTokenizer = null
        tgtTokenizer = null
        Log.i(TAG, "Translator released")
    }

    val isLoaded: Boolean get() = encoderSession != null
}
