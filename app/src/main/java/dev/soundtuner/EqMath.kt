package dev.soundtuner

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType { PEAK, LOW_SHELF, HIGH_SHELF }

/** One parametric EQ filter, same format as Equalizer APO / AutoEq. */
data class Filter(val type: FilterType, val freq: Double, val gainDb: Double, val q: Double)

object EqMath {
    private const val SAMPLE_RATE = 48000.0

    /** Gain in dB that a single RBJ biquad filter applies at frequency [f]. */
    fun responseDb(filter: Filter, f: Double): Double {
        val a = 10.0.pow(filter.gainDb / 40.0)
        val w0 = 2 * PI * filter.freq / SAMPLE_RATE
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * filter.q)

        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1: Double; val a2: Double
        when (filter.type) {
            FilterType.PEAK -> {
                b0 = 1 + alpha * a; b1 = -2 * cw; b2 = 1 - alpha * a
                a0 = 1 + alpha / a; a1 = -2 * cw; a2 = 1 - alpha / a
            }
            FilterType.LOW_SHELF -> {
                val s = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) - (a - 1) * cw + s)
                b1 = 2 * a * ((a - 1) - (a + 1) * cw)
                b2 = a * ((a + 1) - (a - 1) * cw - s)
                a0 = (a + 1) + (a - 1) * cw + s
                a1 = -2 * ((a - 1) + (a + 1) * cw)
                a2 = (a + 1) + (a - 1) * cw - s
            }
            FilterType.HIGH_SHELF -> {
                val s = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) + (a - 1) * cw + s)
                b1 = -2 * a * ((a - 1) + (a + 1) * cw)
                b2 = a * ((a + 1) + (a - 1) * cw - s)
                a0 = (a + 1) - (a - 1) * cw + s
                a1 = 2 * ((a - 1) - (a + 1) * cw)
                a2 = (a + 1) - (a - 1) * cw - s
            }
        }

        val w = 2 * PI * f / SAMPLE_RATE
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2; val ni = -(b1 * s1 + b2 * s2)
        val dr = a0 + a1 * c1 + a2 * c2; val di = -(a1 * s1 + a2 * s2)
        return 10 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }

    /** Combined gain in dB of all filters at frequency [f]. */
    fun curveDb(filters: List<Filter>, f: Double): Double = filters.sumOf { responseDb(it, f) }

    /** Log-spaced frequency between 20 Hz (t = 0) and 20 kHz (t = 1). */
    fun logFreq(t: Double): Double = 20.0 * 1000.0.pow(t)

    /** Highest boost anywhere in the audible range - used to set headroom so nothing clips. */
    fun peakDb(filters: List<Filter>): Double =
        (0..240).maxOf { curveDb(filters, logFreq(it / 240.0)) }
}
