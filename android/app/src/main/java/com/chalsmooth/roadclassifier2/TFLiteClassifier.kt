package com.chalsmooth.roadclassifier2

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Runs assets/model.tflite: input [1, 93] float32 raw features
 * (order = ml/models/feature_cols.json), output [1, 4] softmax for
 * 0 pothole, 1 speed_bump, 2 uneven_road, 3 normal.
 * Normalization is baked into the model; no scaler asset needed.
 */
class TFLiteClassifier(context: Context) {

    companion object {
        private const val TAG = "TFLiteClassifier"
        private const val MODEL_PATH = "model.tflite"
        const val FEATURE_COUNT = 93
        const val CLASS_COUNT = 4

        val DISPLAY_NAMES = arrayOf(
            "Pothole detected",
            "Speed bump detected",
            "Uneven road",
            "Normal road"
        )
        // pothole red, speed bump orange, uneven blue, normal green
        val COLORS = intArrayOf(0xFFF44336.toInt(), 0xFFFF9800.toInt(), 0xFF2196F3.toInt(), 0xFF4CAF50.toInt())
    }

    private val interpreter: Interpreter?
    private val lock = Any()

    init {
        var tmp: Interpreter? = null
        try {
            context.assets.openFd(MODEL_PATH).use { fd ->
                FileInputStream(fd.fileDescriptor).use { fis ->
                    val mapped = fis.channel.map(
                        FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
                    )
                    tmp = Interpreter(mapped, Interpreter.Options().apply { numThreads = 4 })
                }
            }
            Log.d(TAG, "model loaded")
        } catch (e: Exception) {
            Log.e(TAG, "model load failed", e)
        }
        interpreter = tmp
    }

    val isReady: Boolean get() = interpreter != null

    /** @return Pair(predictedIndex, probabilities[4]) or null on error. */
    fun classify(features: FloatArray): Pair<Int, FloatArray>? {
        val interp = interpreter ?: return null
        if (features.size != FEATURE_COUNT) {
            Log.e(TAG, "bad feature size ${features.size}")
            return null
        }
        return synchronized(lock) {
            try {
                val input = ByteBuffer.allocateDirect(FEATURE_COUNT * 4).order(ByteOrder.nativeOrder())
                for (f in features) input.putFloat(if (f.isFinite()) f else 0f)
                input.rewind()
                val output = ByteBuffer.allocateDirect(CLASS_COUNT * 4).order(ByteOrder.nativeOrder())
                interp.run(input, output)
                output.rewind()
                val probs = FloatArray(CLASS_COUNT) { output.float }
                var best = 0
                for (i in 1 until CLASS_COUNT) if (probs[i] > probs[best]) best = i
                Pair(best, probs)
            } catch (e: Exception) {
                Log.e(TAG, "inference failed", e)
                null
            }
        }
    }

    fun close() {
        try { interpreter?.close() } catch (_: Exception) { }
    }
}
