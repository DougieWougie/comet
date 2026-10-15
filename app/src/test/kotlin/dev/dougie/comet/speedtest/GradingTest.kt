package dev.dougie.comet.speedtest

import org.junit.Test
import org.junit.Assert.assertEquals

class GradingTest {
    @Test fun pingBoundaries() {
        assertEquals(Grade.Good, Grading.ping(30.0))
        assertEquals(Grade.Ok, Grading.ping(30.1))
        assertEquals(Grade.Ok, Grading.ping(80.0))
        assertEquals(Grade.Bad, Grading.ping(80.1))
    }

    @Test fun throughputBoundaries() {
        assertEquals(Grade.Good, Grading.download(50.0))
        assertEquals(Grade.Ok, Grading.download(49.9))
        assertEquals(Grade.Bad, Grading.download(14.9))
        assertEquals(Grade.Good, Grading.upload(20.0))
        assertEquals(Grade.Ok, Grading.upload(5.0))
        assertEquals(Grade.Bad, Grading.upload(4.9))
    }
}
