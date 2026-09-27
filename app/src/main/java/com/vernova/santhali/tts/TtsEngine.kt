package com.vernova.santhali.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Text-to-Speech engine for both Hindi and Santhali.
 *
 * Hindi TTS:    Android built-in TTS (10–50 MB, no download needed)
 * Santhali TTS: Android TTS with Santhali locale fallback.
 *               (Piper ONNX sat model not yet publicly available — using
 *                Android TTS with eSpeak-NG phoneme rules as fallback.)
 *
 * Audio output: 22–24 kHz WAV (handled by Android AudioTrack internally).
 * Runtime RAM: ~50–150 MB
 */
class TtsEngine(private val context: Context) {

    companion object {
        private const val TAG = "TtsEngine"
        private val LOCALE_HINDI    = Locale("hi", "IN")
        private val LOCALE_SANTHALI = Locale("sat", "IN")
    }

    private var tts: TextToSpeech? = null
    private var isReady = false

    /**
     * Initialize the TTS engine. Must be called before speak().
     * Returns true if initialization succeeded.
     */
    suspend fun initialize(): Boolean = suspendCancellableCoroutine { cont ->
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                Log.i(TAG, "TTS initialized")
                cont.resume(true)
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
                cont.resume(false)
            }
        }
    }

    /**
     * Speak [text] in the given [lang] (e.g. "hi" or "sat").
     * Suspends until speech is complete.
     */
    suspend fun speak(text: String, lang: String) = suspendCancellableCoroutine<Unit> { cont ->
        val engine = tts ?: run { cont.resume(Unit); return@suspendCancellableCoroutine }

        val locale = when (lang) {
            "hi"  -> LOCALE_HINDI
            "sat" -> LOCALE_SANTHALI
            "te"  -> Locale("te", "IN")
            "ta"  -> Locale("ta", "IN")
            else  -> Locale(lang, "IN")
        }

        val setResult = engine.setLanguage(locale)
        if (setResult == TextToSpeech.LANG_MISSING_DATA || setResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Graceful fallback: try Hindi for Santhali (closest available)
            Log.w(TAG, "Language $lang not supported by TTS — falling back to Hindi")
            engine.setLanguage(LOCALE_HINDI)
        }

        // Adjust speech rate slightly for regional languages
        engine.setSpeechRate(0.9f)
        engine.setPitch(1.0f)

        val utteranceId = "UTT_${System.currentTimeMillis()}"

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) cont.resume(Unit)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) cont.resumeWithException(RuntimeException("TTS error for utterance $id"))
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        Log.d(TAG, "Speaking [$lang]: \"${text.take(60)}...\"")

        cont.invokeOnCancellation { engine.stop() }
    }

    /** Stop any ongoing speech immediately. */
    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Log.i(TAG, "TTS released")
    }

    val ready: Boolean get() = isReady
}
