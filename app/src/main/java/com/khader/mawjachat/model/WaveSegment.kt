package com.khader.mawjachat.model

data class WaveSegment(
    val type: String,
    val label: String,
    val startMs: Long,
    val durationMs: Long,
    val peakAmplitude: Float,
    val rmsEnergy: Float,
    val zeroCrossingRate: Float,
    val dominantFreqHz: Float
)