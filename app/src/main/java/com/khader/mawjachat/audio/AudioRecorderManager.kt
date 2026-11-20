package com.khader.mawjachat.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

class AudioRecorderManager(
    private val onLiveAmplitude: (Float) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private val pcmStream = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val sampleRate = AcousticWaveTranslator.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

        pcmStream.reset()
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    synchronized(pcmStream) {
                        pcmStream.write(buffer, 0, read)
                    }
                    var sum = 0.0
                    var i = 0
                    while (i + 1 < read) {
                        val s = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort() / 32768.0
                        sum += s * s
                        i += 2
                    }
                    val rms = sqrt(sum / (read / 2.0)).toFloat()
                    onLiveAmplitude((rms * 4.5f).coerceIn(0.02f, 1.0f))
                }
            }
        }.apply { start() }
    }

    fun stopRecording(): ByteArray {
        isRecording = false
        try {
            recordingThread?.join(400)
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        } finally {
            audioRecord = null
        }
        synchronized(pcmStream) {
            return pcmStream.toByteArray()
        }
    }
}