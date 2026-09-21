package com.chalsmooth.roadclassifier

import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simple road-condition detector:
 * collects accelerometer (+ gyroscope for display) -> 128-sample window
 * -> 93 features (FeatureExtractor, same math as ml/trainer.py)
 * -> assets/model.tflite -> UI shows pothole / speed bump / uneven / normal.
 *
 * Gyroscope is display-only; the model uses accelerometer features.
 */
class MainActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val INFERENCE_INTERVAL_MS = 500L
    }

    private lateinit var tvClassification: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvAccel: TextView
    private lateinit var tvGyro: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var llProbabilities: LinearLayout
    private lateinit var cardProbabilities: View

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private val accelBuffer = ArrayDeque<FloatArray>(512)
    private val bufferLock = Any()
    private val detecting = AtomicBoolean(false)
    private var hasGyro = false
    private var lastInferenceAt = 0L

    private var classifier: TFLiteClassifier? = null
    private val featureExtractor = FeatureExtractor()
    private var executor: ExecutorService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvClassification = findViewById(R.id.tvClassification)
        tvConfidence = findViewById(R.id.tvConfidence)
        tvAccel = findViewById(R.id.tvAccel)
        tvGyro = findViewById(R.id.tvGyro)
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        llProbabilities = findViewById(R.id.llProbabilities)
        cardProbabilities = findViewById(R.id.cardProbabilities)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        executor = Executors.newSingleThreadExecutor()
        classifier = TFLiteClassifier(this)

        if (accelerometer == null) {
            tvStatus.text = "No accelerometer found on this device."
            btnStart.isEnabled = false
            return
        }
        if (!classifier!!.isReady) {
            tvStatus.text = "Failed to load AI model."
            btnStart.isEnabled = false
            return
        }

        btnStart.setOnClickListener { startDetection() }
        btnStop.setOnClickListener { stopDetection() }
        updateButtons()
    }

    private fun startDetection() {
        if (detecting.getAndSet(true)) return
        synchronized(bufferLock) { accelBuffer.clear() }
        lastInferenceAt = 0L
        hasGyro = false
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        gyroscope?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        tvStatus.text = getString(R.string.collecting_data)
        updateButtons()
    }

    private fun stopDetection() {
        if (!detecting.getAndSet(false)) return
        sensorManager?.unregisterListener(this)
        tvStatus.text = getString(R.string.waiting_for_data)
        updateButtons()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!detecting.get()) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val sample = floatArrayOf(event.values[0], event.values[1], event.values[2])
                var snapshot: List<FloatArray>? = null
                synchronized(bufferLock) {
                    accelBuffer.addLast(sample)
                    while (accelBuffer.size > 512) accelBuffer.removeFirst()
                    val now = System.currentTimeMillis()
                    if (accelBuffer.size >= FeatureExtractor.WINDOW_SIZE &&
                        now - lastInferenceAt >= INFERENCE_INTERVAL_MS
                    ) {
                        lastInferenceAt = now
                        snapshot = accelBuffer.toList()
                    }
                }
                runOnUiThread {
                    tvAccel.text = "X: %.2f  Y: %.2f  Z: %.2f".format(sample[0], sample[1], sample[2])
                    if (detecting.get()) {
                        val n = synchronized(bufferLock) { accelBuffer.size }
                        if (n < FeatureExtractor.WINDOW_SIZE) {
                            tvStatus.text = "Collecting… $n/${FeatureExtractor.WINDOW_SIZE} samples"
                        }
                    }
                }
                snapshot?.let { runInference(it) }
            }
            Sensor.TYPE_GYROSCOPE -> {
                hasGyro = true
                val g = event.values
                runOnUiThread {
                    tvGyro.text = "X: %.2f  Y: %.2f  Z: %.2f".format(g[0], g[1], g[2])
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun runInference(window: List<FloatArray>) {
        val exec = executor ?: return
        exec.execute {
            val features = featureExtractor.extractFeatures(window) ?: return@execute
            val result = classifier?.classify(features) ?: return@execute
            runOnUiThread { showResult(result.first, result.second, window.size) }
        }
    }

    private fun showResult(predicted: Int, probs: FloatArray, buffered: Int) {
        if (!detecting.get()) return
        val names = TFLiteClassifier.DISPLAY_NAMES
        val colors = TFLiteClassifier.COLORS
        if (predicted in names.indices) {
            tvClassification.text = names[predicted]
            tvClassification.setTextColor(colors[predicted])
            tvConfidence.text = "%.1f%%".format(probs[predicted] * 100)
            tvConfidence.setTextColor(colors[predicted])
        }
        tvStatus.text = if (hasGyro) "Detecting… ($buffered samples)" else "Detecting… ($buffered samples, no gyroscope)"
        cardProbabilities.visibility = View.VISIBLE
        llProbabilities.removeAllViews()
        for (i in probs.indices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this).apply {
                text = names[i]
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = (probs[i] * 100).toInt()
                progressTintList = ColorStateList.valueOf(colors[i])
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)
                    .apply { setMargins(16, 8, 16, 0) }
            }
            val pct = TextView(this).apply {
                text = "%.1f%%".format(probs[i] * 100)
                textSize = 14f
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
            }
            row.addView(label)
            row.addView(bar)
            row.addView(pct)
            llProbabilities.addView(row)
        }
    }

    private fun updateButtons() {
        val running = detecting.get()
        btnStart.isEnabled = !running
        btnStop.isEnabled = running
    }

    override fun onPause() {
        super.onPause()
        // Keep it simple: pause sensor stream when leaving the screen.
        if (detecting.get()) {
            sensorManager?.unregisterListener(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (detecting.get()) {
            accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            gyroscope?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    override fun onDestroy() {
        stopDetection()
        executor?.shutdownNow()
        executor = null
        classifier?.close()
        super.onDestroy()
    }
}
