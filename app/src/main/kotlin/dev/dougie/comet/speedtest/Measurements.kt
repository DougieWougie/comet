package dev.dougie.comet.speedtest

import kotlin.math.abs

object Stats {
    fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    /** Mean after dropping the lowest and highest [trim] fraction of values. */
    fun trimmedMean(values: List<Double>, trim: Double = 0.1): Double {
        require(values.isNotEmpty())
        val s = values.sorted()
        val k = (s.size * trim).toInt()
        val kept = s.subList(k, s.size - k).ifEmpty { s }
        return kept.average()
    }

    /** Mean absolute difference between consecutive samples (RFC 3550 style). */
    fun jitter(values: List<Double>): Double =
        if (values.size < 2) 0.0 else values.zipWithNext { a, b -> abs(b - a) }.average()
}

fun toMbps(bytes: Long, millis: Long): Double =
    if (millis <= 0) 0.0 else bytes * 8.0 / (millis / 1000.0) / 1_000_000.0

/**
 * Turns cumulative byte counts sampled over time into a throughput figure.
 *
 * Everything recorded before [warmupMs] is discarded so TCP slow-start does not drag the
 * result down. The remainder is cut into windows of at least [windowMs]; the reported
 * speed is the trimmed mean of the per-window rates, which ignores stalls and bursts.
 */
class ThroughputMeter(
    private val warmupMs: Long,
    private val windowMs: Long = 250,
    private val liveWindowMs: Long = 1000,
) {
    private val times = ArrayList<Long>()
    private val totals = ArrayList<Long>()

    fun record(elapsedMs: Long, totalBytes: Long) {
        times += elapsedMs
        totals += totalBytes
    }

    /**
     * Speed for the UI. During warm-up it is the rate over the last [liveWindowMs]; once warm-up
     * is over it is the running average since then, so the figure settles instead of jumping.
     */
    fun liveMbps(): Double {
        if (times.size < 2) return 0.0
        val last = times.lastIndex
        val first = times.indexOfFirst { it >= warmupMs }
        if (first in 0 until last && times[last] - times[first] >= liveWindowMs / 2) {
            return toMbps(totals[last] - totals[first], times[last] - times[first])
        }
        var i = last - 1
        while (i > 0 && times[last] - times[i] < liveWindowMs) i--
        return toMbps(totals[last] - totals[i], times[last] - times[i])
    }

    fun windowRates(afterMs: Long = warmupMs): List<Double> {
        val rates = ArrayList<Double>()
        var start = times.indexOfFirst { it >= afterMs }
        if (start < 0) return rates
        for (j in start + 1..times.lastIndex) {
            if (times[j] - times[start] >= windowMs) {
                rates += toMbps(totals[j] - totals[start], times[j] - times[start])
                start = j
            }
        }
        return rates
    }

    /** Null if no data was recorded. If the run was shorter than the warm-up, uses everything. */
    fun result(): Double? {
        val rates = windowRates().ifEmpty { windowRates(afterMs = 0) }
        return if (rates.isEmpty()) null else Stats.trimmedMean(rates)
    }
}
