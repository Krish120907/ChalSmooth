package com.chalsmooth.roadclassifier2

import android.util.Log
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Extracts the 93 raw accelerometer features the TFLite model expects.
 *
 * MUST stay in sync with ml/trainer.py::extract_features():
 *  - population std (divide by N, like np.std default), NOT sample std
 *  - IQR via linear-interpolation percentiles (like np.percentile default)
 *  - skew = mean(((x-mean)/std)^3), kurtosis = mean(((x-mean)/std)^4) - 3
 *  - jerk = raw sample-to-sample diff (np.diff), NOT scaled by sample rate
 *  - peak_count = #(acc_mag > mean + 2*std), NOT a local-maxima count
 *  - sign changes computed on MEAN-CENTERED axes with np.sign semantics
 *    (sign(0) == 0 counts as its own state)
 *
 * Feature order matches ml/models/feature_cols.json exactly:
 * acc_{x,y,z,mag} x 11 stats, jerk_{x,y,z,mag} x 11 stats,
 * then peak_count, max_abs_magnitude, acc_{x,y,z}_sign_changes.
 */
class FeatureExtractor {
    companion object {
        private const val TAG = "FeatureExtractor"
        const val WINDOW_SIZE = 128
        const val FEATURE_COUNT = 93
    }

    fun extractFeatures(accelData: List<FloatArray>): FloatArray? {
        // Drop rows with non-finite values (mirrors trainer.py valid-mask).
        val clean = accelData.filter { it.size >= 3 && it[0].isFinite() && it[1].isFinite() && it[2].isFinite() }
        if (clean.size < WINDOW_SIZE) {
            Log.d(TAG, "Insufficient data: ${clean.size} < $WINDOW_SIZE")
            return null
        }

        val window = clean.takeLast(WINDOW_SIZE)
        val accX = FloatArray(WINDOW_SIZE) { window[it][0] }
        val accY = FloatArray(WINDOW_SIZE) { window[it][1] }
        val accZ = FloatArray(WINDOW_SIZE) { window[it][2] }

        val accMag = FloatArray(WINDOW_SIZE) { i ->
            sqrt(accX[i] * accX[i] + accY[i] * accY[i] + accZ[i] * accZ[i])
        }

        // Raw sample jerk (np.diff) -- no sample-rate scaling.
        val jerkX = diff(accX)
        val jerkY = diff(accY)
        val jerkZ = diff(accZ)
        val jerkMag = FloatArray(jerkX.size) { i ->
            sqrt(jerkX[i] * jerkX[i] + jerkY[i] * jerkY[i] + jerkZ[i] * jerkZ[i])
        }

        val out = FloatArray(FEATURE_COUNT)
        var idx = 0
        for (v in listOf(accX, accY, accZ, accMag)) idx = putStats(out, idx, v)
        for (v in listOf(jerkX, jerkY, jerkZ, jerkMag)) idx = putStats(out, idx, v)

        // peak_count = #(mag > mean + 2*std), population std like np.std.
        val magMean = mean(accMag)
        val magStd = std(accMag)
        var peaks = 0
        for (v in accMag) if (v > magMean + 2 * magStd) peaks++
        out[idx++] = peaks.toFloat()
        out[idx++] = maxAbs(accMag)
        out[idx++] = signChangesCentered(accX).toFloat()
        out[idx++] = signChangesCentered(accY).toFloat()
        out[idx++] = signChangesCentered(accZ).toFloat()

        if (idx != FEATURE_COUNT) Log.e(TAG, "Feature count mismatch: $idx != $FEATURE_COUNT")
        return out
    }

    private fun putStats(out: FloatArray, start: Int, v: FloatArray): Int {
        var i = start
        val m = mean(v)
        val s = std(v)
        out[i++] = m
        out[i++] = s
        out[i++] = min(v)
        out[i++] = max(v)
        out[i++] = median(v)
        out[i++] = max(v) - min(v)
        out[i++] = percentile(v, 75f) - percentile(v, 25f)
        out[i++] = sqrt(meanOfSquares(v))
        out[i++] = sumOfSquares(v)
        if (s > 0f) {
            var skew = 0.0
            var kurt = 0.0
            for (x in v) {
                val z = (x - m) / s
                skew += z * z * z
                kurt += z * z * z * z
            }
            out[i++] = (skew / v.size).toFloat()
            out[i++] = (kurt / v.size).toFloat() - 3f
        } else {
            out[i++] = 0f
            out[i++] = 0f
        }
        return i
    }

    private fun diff(v: FloatArray): FloatArray =
        FloatArray(v.size - 1) { i -> v[i + 1] - v[i] }

    private fun mean(v: FloatArray): Float {
        if (v.isEmpty()) return 0f
        var sum = 0.0
        for (x in v) sum += x
        return (sum / v.size).toFloat()
    }

    /** Population std (ddof=0), matching np.std default. */
    private fun std(v: FloatArray): Float {
        if (v.isEmpty()) return 0f
        val m = mean(v).toDouble()
        var sum = 0.0
        for (x in v) {
            val d = x - m
            sum += d * d
        }
        return sqrt(sum / v.size).toFloat()
    }

    private fun min(v: FloatArray): Float {
        var m = v[0]
        for (x in v) if (x < m) m = x
        return m
    }

    private fun max(v: FloatArray): Float {
        var m = v[0]
        for (x in v) if (x > m) m = x
        return m
    }

    private fun median(v: FloatArray): Float {
        val s = v.copyOf()
        Arrays.sort(s)
        val n = s.size
        return if (n % 2 == 0) (s[n / 2 - 1] + s[n / 2]) / 2f else s[n / 2]
    }

    /** Linear-interpolation percentile, matching np.percentile default. */
    private fun percentile(v: FloatArray, p: Float): Float {
        if (v.isEmpty()) return 0f
        val s = v.copyOf()
        Arrays.sort(s)
        if (s.size == 1) return s[0]
        val rank = (p / 100f) * (s.size - 1)
        val lo = rank.toInt()
        val hi = minOf(lo + 1, s.size - 1)
        val frac = rank - lo
        return s[lo] + frac * (s[hi] - s[lo])
    }

    private fun meanOfSquares(v: FloatArray): Float {
        var sum = 0.0
        for (x in v) sum += x * x
        return (sum / v.size).toFloat()
    }

    private fun sumOfSquares(v: FloatArray): Float {
        var sum = 0.0
        for (x in v) sum += x * x
        return sum.toFloat()
    }

    private fun maxAbs(v: FloatArray): Float {
        var m = 0f
        for (x in v) {
            val a = abs(x)
            if (a > m) m = a
        }
        return m
    }

    /**
     * Sign changes on mean-centered values with np.sign semantics:
     * sign(x) in {-1, 0, +1}; count i where sign[i] != sign[i-1].
     */
    private fun signChangesCentered(v: FloatArray): Int {
        if (v.size < 2) return 0
        val m = mean(v).toDouble()
        var count = 0
        var prev = signOf(v[0] - m)
        for (i in 1 until v.size) {
            val cur = signOf(v[i] - m)
            if (cur != prev) count++
            prev = cur
        }
        return count
    }

    private fun signOf(d: Double): Int = when {
        d > 0 -> 1
        d < 0 -> -1
        else -> 0
    }
}
