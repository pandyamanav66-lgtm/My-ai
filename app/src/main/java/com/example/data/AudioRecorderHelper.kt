package com.example.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

private const val REC_TAG = "AudioRecorderHelper"

class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    @Volatile
    var isRecording: Boolean = false
        private set

    fun startRecording(): Result<Unit> {
        return try {
            stopAndDiscard()
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            outputFile = file
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(48000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            isRecording = true
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(REC_TAG, "Failed to start audio recording", e)
            stopAndDiscard()
            Result.failure(e)
        }
    }

    fun getNormalizedAmplitude(): Float {
        return try {
            val amp = recorder?.maxAmplitude ?: 0
            (amp / 18000f).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0f
        }
    }

    fun stopAndReadBytes(): Result<ByteArray> {
        val rec = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        isRecording = false
        return try {
            rec?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w(REC_TAG, "MediaRecorder stop threw (recording may be too short)", e)
                }
                release()
            }
            if (file == null || !file.exists() || file.length() < 256L) {
                file?.delete()
                Result.failure(IllegalStateException("Recording was too short. Hold and speak for at least 1 second."))
            } else {
                val bytes = file.readBytes()
                file.delete()
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Log.e(REC_TAG, "Failed to finalize recording", e)
            file?.delete()
            Result.failure(e)
        }
    }

    fun stopAndDiscard() {
        isRecording = false
        try {
            recorder?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
        } catch (_: Exception) {}
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
