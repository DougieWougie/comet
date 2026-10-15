package dev.dougie.comet.speedtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestStateTest {
    @Test fun runningOnlyDuringMeasuringPhases() {
        val running = setOf(Phase.Ping, Phase.Download, Phase.Upload)
        for (phase in Phase.entries) {
            assertEquals("$phase", phase in running, TestState(phase = phase).running)
        }
    }

    @Test fun startsIdleWithNothingMeasured() {
        val s = TestState()
        assertEquals(Phase.Idle, s.phase)
        assertFalse(s.running)
        assertTrue(s.pingMs == null && s.downloadMbps == null && s.uploadMbps == null && s.error == null)
    }
}
