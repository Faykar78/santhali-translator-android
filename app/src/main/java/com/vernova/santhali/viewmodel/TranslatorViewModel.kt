package com.vernova.santhali.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vernova.santhali.audio.AudioRecorder
import com.vernova.santhali.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Translation direction */
enum class Direction { HINDI_TO_SANTHALI, SANTHALI_TO_HINDI }

/** UI state */
data class TranslatorUiState(
    val direction:      Direction    = Direction.HINDI_TO_SANTHALI,
    val phase:          Phase        = Phase.IDLE,
    val sourceText:     String       = "",
    val translatedText: String       = "",
    val errorMessage:   String?      = null,
    val isRecording:    Boolean      = false,
    val modelsReady:    Boolean      = false,
    val copyingModels:  Boolean      = false
)

enum class Phase {
    IDLE,
    RECORDING,
    TRANSCRIBING,    // ASR running
    TRANSLATING,     // IndicTrans2 running
    SPEAKING,        // TTS playing
    DONE,
    ERROR
}

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TranslatorVM"
    }

    private val models   = ModelManager(application)
    private val recorder = AudioRecorder()

    private val _uiState = MutableStateFlow(TranslatorUiState())
    val uiState: StateFlow<TranslatorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Initialize TTS immediately (low RAM, always needed)
            models.initTts()

            // Check if model files exist in internal storage
            val ready = models.areModelsReady()
            _uiState.value = _uiState.value.copy(modelsReady = ready)

            if (!ready) {
                Log.i(TAG, "Models not found in internal storage — copying from APK assets")
                copyModelsFromAssets()
            }
        }
    }

    /** Copy models from APK assets → internal storage (first launch only) */
    private fun copyModelsFromAssets() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(copyingModels = true)
            try {
                models.copyModelsFromAssets()
                val ready = models.areModelsReady()
                _uiState.value = _uiState.value.copy(
                    copyingModels = false,
                    modelsReady   = ready,
                    errorMessage  = if (!ready) "Failed to extract model files from app package." else null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Model copy failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    copyingModels = false,
                    errorMessage  = "Failed to load models: ${e.message}"
                )
            }
        }
    }

    /** Swap translation direction */
    fun swapDirection() {
        val cur = _uiState.value
        if (cur.phase == Phase.RECORDING || cur.phase == Phase.TRANSCRIBING) return
        _uiState.value = cur.copy(
            direction      = if (cur.direction == Direction.HINDI_TO_SANTHALI)
                                Direction.SANTHALI_TO_HINDI
                             else
                                Direction.HINDI_TO_SANTHALI,
            sourceText     = "",
            translatedText = "",
            phase          = Phase.IDLE
        )
    }

    /** Start push-to-talk recording */
    fun startRecording() {
        if (_uiState.value.phase != Phase.IDLE) return
        _uiState.value = _uiState.value.copy(
            phase       = Phase.RECORDING,
            isRecording = true,
            sourceText  = "",
            translatedText = ""
        )
        recorder.startRecording()
        Log.d(TAG, "Recording started")
    }

    /** Stop recording and run the full pipeline: ASR → Translate → TTS */
    fun stopRecordingAndTranslate() {
        viewModelScope.launch {
            runPipeline()
        }
    }

    /** Type-to-translate (text input path) */
    fun translateText(text: String) {
        viewModelScope.launch {
            val dir     = _uiState.value.direction
            val srcLang = if (dir == Direction.HINDI_TO_SANTHALI) "hi" else "sat"
            val tgtLang = if (dir == Direction.HINDI_TO_SANTHALI) "sat" else "hi"

            _uiState.value = _uiState.value.copy(
                phase      = Phase.TRANSLATING,
                sourceText = text
            )
            runTranslateAndSpeak(text, srcLang, tgtLang)
        }
    }

    fun speakTranslation() {
        val state = _uiState.value
        if (state.translatedText.isBlank()) return
        val tgtLang = if (state.direction == Direction.HINDI_TO_SANTHALI) "sat" else "hi"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(phase = Phase.SPEAKING)
            try {
                models.tts.speak(state.translatedText, tgtLang)
            } finally {
                _uiState.value = _uiState.value.copy(phase = Phase.DONE)
            }
        }
    }

    fun speakSource() {
        val state = _uiState.value
        if (state.sourceText.isBlank()) return
        val srcLang = if (state.direction == Direction.HINDI_TO_SANTHALI) "hi" else "sat"
        viewModelScope.launch {
            models.tts.speak(state.sourceText, srcLang)
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null, phase = Phase.IDLE)
    }

    fun reset() {
        _uiState.value = _uiState.value.copy(
            phase = Phase.IDLE, sourceText = "", translatedText = "", errorMessage = null
        )
    }

    // ── Pipeline ─────────────────────────────────────────────────────────────

    private suspend fun runPipeline() {
        val dir     = _uiState.value.direction
        val srcLang = if (dir == Direction.HINDI_TO_SANTHALI) "hi" else "sat"
        val tgtLang = if (dir == Direction.HINDI_TO_SANTHALI) "sat" else "hi"

        try {
            // Step 1: Stop recording → WAV file
            _uiState.value = _uiState.value.copy(phase = Phase.TRANSCRIBING, isRecording = false)
            val wavFile = File(getApplication<Application>().cacheDir, "speech.wav")
            recorder.stopAndSaveWav(wavFile)

            // Step 2: Load ASR (sequential — frees RAM after)
            models.loadAsr(srcLang)
            val transcript = models.asr.transcribe(wavFile)
            models.asr.release()
            wavFile.delete()

            if (transcript.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    phase = Phase.ERROR,
                    errorMessage = "Could not understand speech. Please try again."
                )
                return
            }

            _uiState.value = _uiState.value.copy(sourceText = transcript)

            runTranslateAndSpeak(transcript, srcLang, tgtLang)

        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                phase        = Phase.ERROR,
                isRecording  = false,
                errorMessage = "Error: ${e.message}"
            )
        }
    }

    private suspend fun runTranslateAndSpeak(text: String, srcLang: String, tgtLang: String) {
        try {
            // Step 3: Load translator
            _uiState.value = _uiState.value.copy(phase = Phase.TRANSLATING)
            models.loadTranslator()
            val translated = models.translator.translate(text, srcLang, tgtLang)
            models.translator.release()

            _uiState.value = _uiState.value.copy(translatedText = translated)

            // Step 4: TTS (built-in, always resident)
            _uiState.value = _uiState.value.copy(phase = Phase.SPEAKING)
            models.tts.speak(translated, tgtLang)

            _uiState.value = _uiState.value.copy(phase = Phase.DONE)

        } catch (e: Exception) {
            Log.e(TAG, "Translate/TTS error: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                phase        = Phase.ERROR,
                errorMessage = "Translation error: ${e.message}"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        models.releaseAll()
    }
}
