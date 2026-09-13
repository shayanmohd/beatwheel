package com.mohdshayan.beatwheel.core.drift

data class DriftPoint(val offsetMs: Long, val cents: Float, val gap: Boolean = false)

data class DriftStats(
    val startCents: Float,
    val endCents: Float,
    val minCents: Float,
    val maxCents: Float,
    val centsPerMinute: Float,
    val readingCount: Int,
)

/**
 * Summary of a held-note session. Start and end are the mean of the first and last two seconds of
 * readings so one wobble does not define them; the drift rate is a least-squares slope over every
 * reading, in cents per minute. Gap markers carry no pitch and are ignored.
 */
object DriftSummary {
    private const val EDGE_MS = 2_000L

    fun summarize(points: List<DriftPoint>): DriftStats? {
        val real = points.filter { !it.gap }
        if (real.isEmpty()) return null
        val first = real.first().offsetMs
        val last = real.last().offsetMs
        val start = real.takeWhile { it.offsetMs - first < EDGE_MS }.map { it.cents.toDouble() }.average()
        val end = real.takeLastWhile { last - it.offsetMs < EDGE_MS }.map { it.cents.toDouble() }.average()
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var st = 0.0
        var sc = 0.0
        var stt = 0.0
        var stc = 0.0
        for (p in real) {
            if (p.cents < min) min = p.cents
            if (p.cents > max) max = p.cents
            val t = (p.offsetMs - first) / 60_000.0
            st += t
            sc += p.cents
            stt += t * t
            stc += t * p.cents
        }
        val n = real.size
        val denom = n * stt - st * st
        val slope = if (n >= 2 && denom > 0) (n * stc - st * sc) / denom else 0.0
        return DriftStats(start.toFloat(), end.toFloat(), min, max, slope.toFloat(), n)
    }
}
