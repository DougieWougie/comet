package dev.dougie.comet.speedtest

/** How good a finished result is, for colouring the result moons. */
enum class Grade { Bad, Ok, Good }

object Grading {
    fun ping(ms: Double) = when {
        ms <= 30 -> Grade.Good
        ms <= 80 -> Grade.Ok
        else -> Grade.Bad
    }

    fun download(mbps: Double) = when {
        mbps >= 50 -> Grade.Good
        mbps >= 15 -> Grade.Ok
        else -> Grade.Bad
    }

    fun upload(mbps: Double) = when {
        mbps >= 20 -> Grade.Good
        mbps >= 5 -> Grade.Ok
        else -> Grade.Bad
    }
}
