package dev.dougie.comet.speedtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementsTest {
    @Test fun medianOddAndEven() {
        assertEquals(3.0, Stats.median(listOf(5.0, 1.0, 3.0)), 1e-9)
        assertEquals(2.5, Stats.median(listOf(4.0, 1.0, 2.0, 3.0)), 1e-9)
    }

    @Test fun trimmedMeanDropsOutliers() {
        val v = List(8) { 100.0 } + listOf(1.0, 10_000.0)
        assertEquals(100.0, Stats.trimmedMean(v, 0.1), 1e-9)
    }

    @Test fun jitterIsMeanAbsoluteDelta() {
        assertEquals(2.0, Stats.jitter(listOf(10.0, 12.0, 10.0, 12.0)), 1e-9)
        assertEquals(0.0, Stats.jitter(listOf(10.0)), 1e-9)
    }

    @Test fun meterDiscardsSlowStart() {
        // 0-2s ramp at 10 Mbps, then a steady 100 Mbps (12.5 MB/s) for 8s.
        val m = ThroughputMeter(warmupMs = 2_000)
        var bytes = 0L
        for (t in 100..10_000 step 100) {
            bytes += if (t <= 2_000) 125_000 else 1_250_000
            m.record(t.toLong(), bytes)
        }
        assertEquals(100.0, m.result()!!, 1.0)
    }

    @Test fun meterShortRunFallsBackToAllData() {
        val m = ThroughputMeter(warmupMs = 2_000)
        for (t in 100..1_000 step 100) m.record(t.toLong(), t * 1_250L) // 10 Mbps
        assertEquals(10.0, m.result()!!, 0.5)
    }

    @Test fun meterWithoutDataHasNoResult() {
        assertNull(ThroughputMeter(2_000).result())
    }

    @Test fun liveSpeedUsesRecentWindowDuringWarmup() {
        val m = ThroughputMeter(warmupMs = 5_000)
        m.record(0, 0)
        m.record(1_000, 1_250_000) // 10 Mbps
        m.record(2_000, 1_250_000 + 12_500_000) // 100 Mbps over the last second
        assertEquals(100.0, m.liveMbps(), 0.1)
    }

    @Test fun liveSpeedIsRunningAverageAfterWarmup() {
        val m = ThroughputMeter(warmupMs = 1_000)
        m.record(0, 0)
        m.record(1_000, 0)
        m.record(2_000, 1_250_000) // 10 Mbps
        m.record(3_000, 1_250_000 + 12_500_000) // 100 Mbps burst
        assertEquals(55.0, m.liveMbps(), 0.1)
    }
}
