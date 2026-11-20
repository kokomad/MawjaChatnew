package com.khader.mawjachat.model

data class WaveMessagePacket(
    val messageId: String,
    val roomId: String,
    val senderName: String,
    val receiverName: String,
    val timestamp: Long,
    val sampleRate: Int = 16000,
    val totalDurationMs: Long,
    val phoneticTranscript: String,
    val segments: List<WaveSegment>,
    val compressedPcmBase64: String
)