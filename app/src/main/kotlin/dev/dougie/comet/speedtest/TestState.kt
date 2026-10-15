package dev.dougie.comet.speedtest

enum class Phase { Idle, Ping, Download, Upload, Done, Failed }

data class TestState(
    val phase: Phase = Phase.Idle,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    /** Smoothed instantaneous speed of the running throughput phase. */
    val liveMbps: Double = 0.0,
    /** 0..1 progress through the current phase. */
    val progress: Float = 0f,
    val error: String? = null,
) {
    val running get() = phase == Phase.Ping || phase == Phase.Download || phase == Phase.Upload
}
