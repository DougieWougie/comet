package dev.dougie.comet.ui

import dev.dougie.comet.speedtest.Grade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneMathTest {
    @Test fun energyIsZeroWhenIdleAndGrowsWithSpeed() {
        assertEquals(0f, energyForMbps(0.0), 1e-6f)
        assertTrue(energyForMbps(10.0) < energyForMbps(100.0))
        assertTrue(energyForMbps(100.0) < energyForMbps(900.0))
    }

    @Test fun energyIsClamped() {
        assertEquals(1f, energyForMbps(1_000_000.0), 1e-6f)
        assertEquals(0f, energyForMbps(-50.0), 1e-6f)
    }

    @Test fun cometPassesStartAndEndOffScreenAndCrossTheScene() {
        repeat(200) {
            val p = CometPass.random()
            fun offScreen(x: Float, y: Float) = x < 0f || x > 1f || y < 0f || y > 1f
            assertTrue(offScreen(p.fromX, p.fromY))
            assertTrue(offScreen(p.toX, p.toY))
            assertTrue(p.scale in 0.5f..0.8f)
        }
    }

    @Test fun gradeColoursAreDistinctInBothThemes() {
        for (dark in listOf(true, false)) {
            val colours = Grade.entries.map { gradeColor(it, dark) }
            assertEquals(colours.size, colours.toSet().size)
        }
        assertNotEquals(gradeColor(Grade.Good, true), gradeColor(Grade.Good, false))
    }
}
