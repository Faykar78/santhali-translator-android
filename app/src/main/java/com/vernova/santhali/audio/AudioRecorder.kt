package com.vernova.santhali.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records audio at 16 kHz, mono, 16-bit PCM — exactly as required by
 * IndicConformer ASR (sherpa-onnx).
 *
 * Memory strategy: audio is buffered in RAM while recording (push-to-talk),
 * then written as a temporary WAV file for the ASR engine.
 */
class AudioRecorder {

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16_000        // 16 kHz
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val pcmBuffer = ByteArrayOutputStream()

    /** Start buffering audio. Call stopAndGetWav() to finalize. */
    fun startRecording() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufSize = maxOf(minBuf * 4, 8192)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL, ENCODING, bufSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            return
        }

        pcmBuffer.reset()
        isRecording = true
        audioRecord?.startRecording()
        Log.d(TAG, "Recording started at $SAMPLE_RATE Hz")

        val chunk = ByteArray(bufSize)
        Thread {
            while (isRecording) {
                val read = audioRecord?.read(chunk, 0, chunk.size) ?: 0
                if (read > 0) {
                    synchronized(pcmBuffer) { pcmBuffer.write(chunk, 0, read) }
                }
            }
        }.apply { isDaemon = true; start() }
    }

    /**
     * Stop recording and return the audio as a WAV-format ByteArray,
     * ready to be passed to the ASR engine.
     */
    suspend fun stopAndGetWav(): ByteArray = withContext(Dispatchers.IO) {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val pcmBytes = synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
        Log.d(TAG, "Recorded ${pcmBytes.size} PCM bytes (${pcmBytes.size / 2 / SAMPLE_RATE}s)")
        buildWav(pcmBytes)
    }

    /** Write audio to a temp WAV file and return its path (for sherpa-onnx). */
    suspend fun stopAndSaveWav(outputFile: File): File = withContext(Dispatchers.IO) {
        val wavBytes = stopAndGetWav()
        FileOutputStream(outputFile).use { it.write(wavBytes) }
        outputFile
    }

    val isActive: Boolean get() = isRecording

    // ── WAV header builder ───────────────────────────────────────────────────

    private fun buildWav(pcm: ByteArray): ByteArray {
        val totalDataLen = pcm.size + 36
        val buf = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(totalDataLen)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)               // subchunk1 size
        buf.putShort(1)              // PCM format
        buf.putShort(1)              // mono
        buf.putInt(SAMPLE_RATE)
        buf.putInt(SAMPLE_RATE * 2)  // byte rate = sampleRate * blockAlign
        buf.putShort(2)              // blockAlign = channels * bytesPerSample
        buf.putShort(16)             // bits per sample
        buf.put("data".toByteArray())
        buf.putInt(pcm.size)
        buf.put(pcm)
        return buf.array()
    }
}
