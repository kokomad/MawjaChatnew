package com.khader.mawjachat.audio

import android.util.Base64
import com.khader.mawjachat.model.WaveMessagePacket
import com.khader.mawjachat.model.WaveSegment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

object AcousticWaveTranslator {

    const val SAMPLE_RATE = 16000
    private const val FRAME_DURATION_MS = 25
    private const val SAMPLES_PER_FRAME = (SAMPLE_RATE * FRAME_DURATION_MS) / 1000

    private const val SILENCE_RMS_THRESHOLD = 0.022f
    private const val SPACE_MIN_DURATION_MS = 260L
    private const val SILENCE_MIN_DURATION_MS = 60L

    private val arabicPhoneticHints = listOf(
        "أ", "ب", "ت", "ج", "ح", "د", "ر", "س", "ش", "ص", "ع", "ف", "ق", "ك", "ل", "م", "ن", "هـ", "و", "ي"
    )

    fun translatePcmToWavePacket(
        pcmBytes: ByteArray,
        roomId: String,
        senderName: String,
        receiverName: String
    ): WaveMessagePacket {
        val shortCount = pcmBytes.size / 2
        val samples = ShortArray(shortCount)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

        val segments = mutableListOf<WaveSegment>()
        val transcriptBuilder = StringBuilder()

        var index = 0
        var currentTimeMs = 0L
        var silenceStartMs = -1L
        var silenceAccumulatedMs = 0L
        var charIndex = 0

        while (index + SAMPLES_PER_FRAME <= samples.size) {
            var sumSquares = 0.0
            var peak = 0f
            var zeroCrossings = 0

            for (i in 0 until SAMPLES_PER_FRAME) {
                val s = samples[index + i] / 32768.0f
                val absS = abs(s)
                if (absS > peak) peak = absS
                sumSquares += (s * s)
                if (i > 0) {
                    val prev = samples[index + i - 1]
                    val curr = samples[index + i]
                    if ((prev >= 0 && curr < 0) || (prev < 0 && curr >= 0)) {
                        zeroCrossings++
                    }
                }
            }

            val rms = sqrt(sumSquares / SAMPLES_PER_FRAME).toFloat()
            val zcr = zeroCrossings.toFloat() / SAMPLES_PER_FRAME
            val estFreq = (zeroCrossings * SAMPLE_RATE) / (2.0f * SAMPLES_PER_FRAME)

            if (rms < SILENCE_RMS_THRESHOLD) {
                if (silenceStartMs == -1L) {
                    silenceStartMs = currentTimeMs
                }
                silenceAccumulatedMs += FRAME_DURATION_MS
            } else {
                if (silenceAccumulatedMs >= SILENCE_MIN_DURATION_MS) {
                    val isSpace = silenceAccumulatedMs >= SPACE_MIN_DURATION_MS
                    val segType = if (isSpace) "SPACE" else "SILENCE"
                    val label = if (isSpace) "[فراغ ${silenceAccumulatedMs}ms]" else "[سكون ${silenceAccumulatedMs}ms]"
                    segments.add(
                        WaveSegment(
                            type = segType,
                            label = label,
                            startMs = silenceStartMs,
                            durationMs = silenceAccumulatedMs,
                            peakAmplitude = 0.02f,
                            rmsEnergy = 0.01f,
                            zeroCrossingRate = 0f,
                            dominantFreqHz = 0f
                        )
                    )
                    transcriptBuilder.append(if (isSpace) "   " else " · ")
                }
                silenceStartMs = -1L
                silenceAccumulatedMs = 0L

                val phoneticIdx = ((zcr * 45 + rms * 20).toInt() + charIndex) % arabicPhoneticHints.size
                val charLabel = arabicPhoneticHints[abs(phoneticIdx)]
                charIndex++

                segments.add(
                    WaveSegment(
                        type = "CHAR_WAVE",
                        label = charLabel,
                        startMs = currentTimeMs,
                        durationMs = FRAME_DURATION_MS.toLong(),
                        peakAmplitude = peak.coerceIn(0.05f, 1.0f),
                        rmsEnergy = rms,
                        zeroCrossingRate = zcr,
                        dominantFreqHz = estFreq.coerceIn(85f, 3400f)
                    )
                )
                transcriptBuilder.append(charLabel)
            }

            index += SAMPLES_PER_FRAME
            currentTimeMs += FRAME_DURATION_MS
        }

        if (silenceAccumulatedMs >= SILENCE_MIN_DURATION_MS) {
            val isSpace = silenceAccumulatedMs >= SPACE_MIN_DURATION_MS
            segments.add(
                WaveSegment(
                    type = if (isSpace) "SPACE" else "SILENCE",
                    label = if (isSpace) "[فراغ]" else "[سكون]",
                    startMs = silenceStartMs,
                    durationMs = silenceAccumulatedMs,
                    peakAmplitude = 0.01f,
                    rmsEnergy = 0.005f,
                    zeroCrossingRate = 0f,
                    dominantFreqHz = 0f
                )
            )
        }

        val compressedBase64 = compressBytesToBase64(pcmBytes)

        return WaveMessagePacket(
            messageId = UUID.randomUUID().toString(),
            roomId = roomId,
            senderName = senderName,
            receiverName = receiverName,
            timestamp = System.currentTimeMillis(),
            sampleRate = SAMPLE_RATE,
            totalDurationMs = currentTimeMs,
            phoneticTranscript = transcriptBuilder.toString().trim(),
            segments = segments,
            compressedPcmBase64 = compressedBase64
        )
    }

    fun reconstructAudioFile(packet: WaveMessagePacket, outputDir: File): File {
        val wavFile = File(outputDir, "mawja_${packet.messageId}.wav")
        val pcmData = if (packet.compressedPcmBase64.isNotEmpty()) {
            decompressBase64ToBytes(packet.compressedPcmBase64)
        } else {
            synthesizeFromWaveSegments(packet.segments, packet.sampleRate)
        }

        writeWavFile(wavFile, pcmData, packet.sampleRate, 1, 16)
        return wavFile
    }

    private fun synthesizeFromWaveSegments(segments: List<WaveSegment>, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream()
        for (seg in segments) {
            val numSamples = ((seg.durationMs * sampleRate) / 1000L).toInt().coerceAtLeast(1)
            for (i in 0 until numSamples) {
                val sampleShort: Short = if (seg.type == "CHAR_WAVE") {
                    val t = i.toDouble() / sampleRate
                    val wave = sin(2.0 * PI * seg.dominantFreqHz * t) * seg.peakAmplitude * 24000.0
                    wave.toInt().coerceIn(-32767, 32767).toShort()
                } else {
                    0
                }
                out.write(sampleShort.toInt() and 0xFF)
                out.write((sampleShort.toInt() shr 8) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    private fun compressBytesToBase64(data: ByteArray): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    private fun decompressBase64ToBytes(base64Str: String): ByteArray {
        val compressed = Base64.decode(base64Str, Base64.NO_WRAP)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            return gis.readBytes()
        }
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)

        FileOutputStream(file).use { fos ->
            fos.write(header.array())
            fos.write(pcmData)
        }
    }
}