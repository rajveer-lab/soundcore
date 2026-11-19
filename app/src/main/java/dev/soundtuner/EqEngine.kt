package dev.soundtuner

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Turns a list of parametric filters into Android's built-in DynamicsProcessing effect:
 * a 32-band EQ (pre-EQ stage), a preamp for headroom, and a safety limiter.
 *
 * Session 0 is the phone's global output mix (every app). Music apps can also hand us
 * their own audio session via the standard "open audio effect session" broadcast.
 */
class EqEngine {
    companion object {
        private const val TAG = "SoundTuner"
        private const val PRIORITY = 100
        const val BANDS = 32

        /** Upper edge of each band, log-spaced so the last band ends at 20 kHz. */
        private val CUTOFFS = FloatArray(BANDS) { i ->
            (20.0 * 1000.0.pow((i + 1).toDouble() / BANDS)).toFloat()
        }

        /** Geometric centre of band [i] - where we sample the target curve. */
        private fun bandCentre(i: Int): Double {
            val hi = CUTOFFS[i].toDouble()
            val lo = if (i == 0) hi / 1000.0.pow(1.0 / BANDS) else CUTOFFS[i - 1].toDouble()
            return sqrt(lo * hi)
        }
    }

    private val effects = mutableMapOf<Int, DynamicsProcessing>()
    private var gains = FloatArray(BANDS)
    private var preampDb = 0f
    private var active = false

    /** Recalculate the band gains from [filters] and push them to every attached session. */
    fun update(filters: List<Filter>, enabled: Boolean) {
        gains = FloatArray(BANDS) { i ->
            EqMath.curveDb(filters, bandCentre(i)).toFloat().coerceIn(-15f, 15f)
        }
        preampDb = -max(0.0, EqMath.peakDb(filters)).toFloat()
        active = enabled
        effects.values.forEach { configure(it) }
    }

    /** Hook the EQ into an audio session. Returns false if the phone refuses. */
    fun attach(session: Int): Boolean {
        if (effects.containsKey(session)) return true
        return try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,            // stereo
                true, BANDS,  // pre-EQ: our 32 bands
                false, 0,     // multi-band compressor: off
                false, 0,     // post-EQ: off
                true,         // limiter: on (protects drivers from clipping)
            ).setPreferredFrameDuration(10f).build()
            val dp = DynamicsProcessing(PRIORITY, session, config)
            effects[session] = dp
            configure(dp)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not attach to audio session $session", e)
            false
        }
    }

    fun detach(session: Int) {
        if (session == 0) return // keep the global hook while the service runs
        effects.remove(session)?.release()
    }

    fun releaseAll() {
        effects.values.forEach { runCatching { it.release() } }
        effects.clear()
    }

    private fun configure(dp: DynamicsProcessing) {
        try {
            val eq = DynamicsProcessing.Eq(true, true, BANDS)
            for (i in 0 until BANDS) {
                eq.setBand(i, DynamicsProcessing.EqBand(true, CUTOFFS[i], gains[i]))
            }
            dp.setPreEqAllChannelsTo(eq)
            dp.setInputGainAllChannelsTo(preampDb)
            dp.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(
                    true, true, 0,
                    1f,   // attack ms
                    60f,  // release ms
                    10f,  // ratio
                    -1f,  // threshold dB
                    0f,   // post gain dB
                )
            )
            dp.enabled = active
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure EQ", e)
        }
    }
}
