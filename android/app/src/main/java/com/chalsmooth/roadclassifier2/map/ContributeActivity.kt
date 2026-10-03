package com.chalsmooth.roadclassifier2.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chalsmooth.roadclassifier2.R
import com.chalsmooth.roadclassifier2.ai.DetectionResult
import com.chalsmooth.roadclassifier2.ai.YoloPotholeDetector
import com.chalsmooth.roadclassifier2.data.PotholeDatabaseHelper
import com.chalsmooth.roadclassifier2.data.PotholeEntry
import com.chalsmooth.roadclassifier2.location.LocationManager
import com.chalsmooth.roadclassifier2.location.LocationWithBearing
import com.chalsmooth.roadclassifier2.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ContributeActivity : AppCompatActivity() {

    private lateinit var tvGpsStatus: TextView
    private lateinit var layoutPlaceholder: View
    private lateinit var ivCapturedImage: ImageView
    private lateinit var layoutAnalyzing: View
    private lateinit var tvDetectionResult: TextView
    private lateinit var layoutInputButtons: View
    private lateinit var layoutConfirmButtons: View
    private lateinit var btnCapturePhoto: Button
    private lateinit var btnConfirmSubmit: Button
    private lateinit var btnRetake: Button

    private var detector: YoloPotholeDetector? = null
    private var dbHelper: PotholeDatabaseHelper? = null
    private var locationManager: LocationManager? = null

    private var currentLocation: LatLng? = null
    private var originalBitmap: Bitmap? = null
    private var annotatedBitmap: Bitmap? = null
    private var detectedList: List<DetectionResult> = emptyList()

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { processImage(it) }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contribute)

        initViews()
        initServices()
        checkPermissions()
    }

    private fun initViews() {
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
        layoutPlaceholder = findViewById(R.id.layoutPlaceholder)
        ivCapturedImage = findViewById(R.id.ivCapturedImage)
        layoutAnalyzing = findViewById(R.id.layoutAnalyzing)
        tvDetectionResult = findViewById(R.id.tvDetectionResult)
        layoutInputButtons = findViewById(R.id.layoutInputButtons)
        layoutConfirmButtons = findViewById(R.id.layoutConfirmButtons)
        btnCapturePhoto = findViewById(R.id.btnCapturePhoto)
        btnConfirmSubmit = findViewById(R.id.btnConfirmSubmit)
        btnRetake = findViewById(R.id.btnRetake)

        findViewById<LinearLayout>(R.id.btnNavHome).setOnClickListener {
            finish()
            overridePendingTransition(0, 0)
        }

        btnCapturePhoto.setOnClickListener {
            takePhotoLauncher.launch(null)
        }

        btnRetake.setOnClickListener {
            resetImageSelection()
        }

        btnConfirmSubmit.setOnClickListener {
            submitPothole()
        }
    }

    private fun initServices() {
        detector = YoloPotholeDetector(this)
        dbHelper = PotholeDatabaseHelper(this)
        locationManager = LocationManager(this, this)
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        locationManager?.let { mgr ->
            mgr.startLocationUpdates()
            lifecycleScope.launch {
                mgr.getLocationChannel().consumeEach { loc: LocationWithBearing ->
                    currentLocation = loc.latLng
                    tvGpsStatus.text = String.format("GPS: %.4f, %.4f", loc.latLng.latitude, loc.latLng.longitude)
                }
            }
            val lastLoc = mgr.getLastKnownLocation()
            if (lastLoc != null) {
                currentLocation = lastLoc.latLng
                tvGpsStatus.text = String.format("GPS: %.4f, %.4f", lastLoc.latLng.latitude, lastLoc.latLng.longitude)
            }
        }
    }

    private fun processImage(bitmap: Bitmap) {
        originalBitmap = bitmap
        layoutAnalyzing.visibility = View.VISIBLE
        layoutPlaceholder.visibility = View.GONE
        ivCapturedImage.visibility = View.VISIBLE
        ivCapturedImage.setImageBitmap(bitmap)
        layoutInputButtons.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.Default) {
            val results = detector?.detect(bitmap) ?: emptyList()
            detectedList = results

            val outputBitmap = if (results.isNotEmpty()) {
                detector?.drawDetectionsOnBitmap(bitmap, results) ?: bitmap
            } else {
                bitmap
            }
            annotatedBitmap = outputBitmap

            withContext(Dispatchers.Main) {
                layoutAnalyzing.visibility = View.GONE
                ivCapturedImage.setImageBitmap(outputBitmap)
                tvDetectionResult.visibility = View.VISIBLE
                layoutConfirmButtons.visibility = View.VISIBLE

                if (results.isNotEmpty()) {
                    val topResult = results.maxByOrNull { it.score }
                    val label = topResult?.label ?: "Pothole"
                    val confPercent = ((topResult?.score ?: 0f) * 100).toInt()
                    tvDetectionResult.setTextColor(Color.parseColor("#2E7D32"))
                    tvDetectionResult.text = "Detected ${results.size} $label (Confidence: $confPercent%)"

                    // Enable and show submission button only when potholes are detected
                    btnConfirmSubmit.visibility = View.VISIBLE
                    btnConfirmSubmit.isEnabled = true
                } else {
                    tvDetectionResult.setTextColor(Color.parseColor("#D32F2F"))
                    tvDetectionResult.text = "No pothole detected in this image.\nSubmission is disabled."

                    // Hide and disable submission button if no pothole is detected
                    btnConfirmSubmit.visibility = View.GONE
                    btnConfirmSubmit.isEnabled = false
                }
            }
        }
    }

    private fun resetImageSelection() {
        originalBitmap = null
        annotatedBitmap = null
        detectedList = emptyList()

        ivCapturedImage.setImageBitmap(null)
        ivCapturedImage.visibility = View.GONE
        layoutPlaceholder.visibility = View.VISIBLE
        tvDetectionResult.visibility = View.GONE
        layoutConfirmButtons.visibility = View.GONE
        layoutInputButtons.visibility = View.VISIBLE
    }

    private fun submitPothole() {
        if (detectedList.isEmpty()) {
            Toast.makeText(this, "Cannot submit: No pothole detected in the photo.", Toast.LENGTH_SHORT).show()
            return
        }

        val location = currentLocation
        if (location == null) {
            Toast.makeText(this, "Acquiring GPS location... Please wait a moment.", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmapToSave = annotatedBitmap ?: originalBitmap
        val imagePath = saveImageToInternalStorage(bitmapToSave)

        val topDet = detectedList.maxByOrNull { it.score }
        val label = topDet?.label ?: "Pothole"
        val conf = topDet?.score ?: 1.0f

        val entry = PotholeEntry(
            id = UUID.randomUUID().toString(),
            latitude = location.latitude,
            longitude = location.longitude,
            label = label,
            confidence = conf,
            imagePath = imagePath,
            timestamp = System.currentTimeMillis()
        )

        val success = dbHelper?.insertPothole(entry) ?: false
        if (success) {
            lifecycleScope.launch {
                com.chalsmooth.roadclassifier2.network.PotholeApiClient(this@ContributeActivity).postPothole(entry)
            }
            Toast.makeText(this, "Pothole reported successfully and shared with server!", Toast.LENGTH_LONG).show()
            finish()
            overridePendingTransition(0, 0)
        } else {
            Toast.makeText(this, "Failed to save pothole entry", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap?): String {
        if (bitmap == null) return ""
        return try {
            val dir = File(filesDir, "potholes")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "pothole_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        locationManager?.stopLocationUpdates()
    }
}
