package com.example.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val SOUND_TAG = "FocusSoundEngine"
private const val SAMPLE_RATE = 22050
private const val CHUNK_SAMPLES = 2048

/**
 * 100% offline, zero-asset procedural ambient focus sound generator using Android AudioTrack.
 * Produces smooth, non-fatiguing Rain, Brown Noise, and Warm Lo-Fi pad soundscapes
 * with automatic error resilience on emulators and headless JVM tests.
 */
class FocusSoundEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playJob: Job? = null

    @Volatile
    private var activeMode: FocusSoundMode = FocusSoundMode.OFF

    fun start(mode: FocusSoundMode) {
        if (mode == FocusSoundMode.OFF) {
            stop()
            return
        }
        if (activeMode == mode && playJob?.isActive == true) return
        stop()
        activeMode = mode

        playJob = scope.launch {
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) return@launch
                val bufferBytes = maxOf(minBuf * 2, CHUNK_SAMPLES * 2)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()

                val pcm = ShortArray(CHUNK_SAMPLES)
                val rng = Random(System.nanoTime())
                var brown = 0.0
                var lp1 = 0.0
                var lp2 = 0.0
                var phaseIdx = 0L
                var fadeIn = 0.0

                while (isActive && activeMode == mode) {
                    when (mode) {
                        FocusSoundMode.RAIN -> {
                            for (i in 0 until CHUNK_SAMPLES) {
                                val white = rng.nextDouble() * 2.0 - 1.0
                                // Warm 2-stage low-pass filter for cozy steady rainfall
                                lp1 = 0.86 * lp1 + 0.14 * white
                                lp2 = 0.72 * lp2 + 0.28 * lp1
                                // Gentle soft raindrop shimmer
                                val shimmer = if (rng.nextInt(1400) == 0) (rng.nextDouble() - 0.5) * 0.24 else 0.0
                                val env = 0.85 + 0.15 * sin(2.0 * PI * (phaseIdx + i) / (SAMPLE_RATE * 7.5))
                                fadeIn = (fadeIn + 0.00018).coerceAtMost(1.0)
                                val sample = ((lp2 * 1.45 + shimmer) * env * fadeIn * 0.24).coerceIn(-0.95, 0.95)
                                pcm[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                            }
                        }

                        FocusSoundMode.BROWN_NOISE -> {
                            for (i in 0 until CHUNK_SAMPLES) {
                                val white = rng.nextDouble() * 2.0 - 1.0
                                brown = (brown + 0.025 * white) / 1.025
                                lp1 = 0.90 * lp1 + 0.10 * brown
                                fadeIn = (fadeIn + 0.00018).coerceAtMost(1.0)
                                val sample = (lp1 * 2.6 * fadeIn * 0.30).coerceIn(-0.95, 0.95)
                                pcm[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                            }
                        }

                        FocusSoundMode.LOFI_PAD -> {
                            // Warm Cmaj7 ambient study pad with slow breathing LFO + subtle cozy vinyl noise
                            for (i in 0 until CHUNK_SAMPLES) {
                                val t = (phaseIdx + i).toDouble() / SAMPLE_RATE.toDouble()
                                val breath = 0.72 + 0.28 * sin(2.0 * PI * 0.11 * t)
                                val pad =
                                    0.42 * sin(2.0 * PI * 130.81 * t) + // C3
                                        0.28 * sin(2.0 * PI * 196.00 * t) + // G3
                                        0.18 * sin(2.0 * PI * 246.94 * t) + // B3
                                        0.12 * sin(2.0 * PI * 329.63 * t)   // E4
                                val white = rng.nextDouble() * 2.0 - 1.0
                                lp1 = 0.92 * lp1 + 0.08 * white
                                fadeIn = (fadeIn + 0.00018).coerceAtMost(1.0)
                                val sample = ((pad * 0.14 * breath) + (lp1 * 0.045)) * fadeIn
                                pcm[i] = (sample.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
                            }
                        }

                        FocusSoundMode.OFF -> break
                    }

                    phaseIdx += CHUNK_SAMPLES
                    val written = track.write(pcm, 0, pcm.size)
                    if (written < 0) break
                }
            } catch (e: Throwable) {
                Log.w(SOUND_TAG, "Focus sound playback skipped/stopped: ${e.localizedMessage}")
            } finally {
                runCatching {
                    track?.pause()
                    track?.flush()
                    track?.stop()
                    track?.release()
                }
            }
        }
    }

    fun stop() {
        activeMode = FocusSoundMode.OFF
        playJob?.cancel()
        playJob = null
    }
}
