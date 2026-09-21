package com.chalsmooth.app.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class ImuSample(
    val ax: Float = 0f,
    val ay: Float = 0f,
    val az: Float = 9.8f,
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 0f,
    val verticalJerk: Float = 0f,
    val detectedAnomaly: String = "Smooth"
)

class ImuSensingService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    private var lastAz = 9.8f
    private var lastTimestamp = 0L

    companion object {
        private val _liveTelemetry = MutableStateFlow(ImuSample())
        val liveTelemetry: StateFlow<ImuSample> = _liveTelemetry

        private const val CHANNEL_ID = "chalsmooth_sensing_channel"
        private const val NOTIF_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        createNotificationChannel()
        startForeground(NOTIF_ID, buildForegroundNotification())

        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            val dt = if (lastTimestamp > 0) (event.timestamp - lastTimestamp) / 1e9f else 0.02f
            val jerk = if (dt > 0) (az - lastAz) / dt else 0f
            lastAz = az
            lastTimestamp = event.timestamp

            var anomaly = "Smooth Road"
            if (kotlin.math.abs(jerk) > 25.0f || kotlin.math.abs(az - 9.8f) > 8.0f) {
                anomaly = "Pothole Shock!"
            } else if (kotlin.math.abs(az - 9.8f) > 3.5f) {
                anomaly = "Speed Bump"
            }

            _liveTelemetry.value = _liveTelemetry.value.copy(
                ax = ax,
                ay = ay,
                az = az,
                verticalJerk = kotlin.math.abs(jerk),
                detectedAnomaly = anomaly
            )
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            _liveTelemetry.value = _liveTelemetry.value.copy(
                gx = event.values[0],
                gy = event.values[1],
                gz = event.values[2]
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChalSmooth Sensing Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChalSmooth Road Sentinel Active")
            .setContentText("Monitoring 50Hz vehicle IMU vibrations for potholes")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}
