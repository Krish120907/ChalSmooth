package com.chalsmooth.roadclassifier2.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chalsmooth.roadclassifier2.R
import com.chalsmooth.roadclassifier2.location.LocationManager
import com.chalsmooth.roadclassifier2.model.LatLng as ModelLatLng
import com.mappls.sdk.core.MapplsInitialiser
import com.mappls.sdk.maps.Mappls
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.geometry.LatLng
import com.mappls.sdk.maps.annotations.Marker
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.annotations.IconFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import android.view.animation.AlphaAnimation

class MapScreen : AppCompatActivity(), OnMapReadyCallback {

    // ── Views ─────────────────────────────────────────────────────────────
    private lateinit var mapView: MapView
    private lateinit var etExploreSearch: EditText
    private lateinit var ivExploreClear: ImageView
    private lateinit var ivNavigate: ImageView
    private lateinit var exploreSearchCard: View
    private lateinit var routingCard: View
    private lateinit var ivBackToExplore: ImageView
    private lateinit var etSource: EditText
    private lateinit var etDestination: EditText
    private lateinit var ivClearSource: ImageView
    private lateinit var ivClearDestination: ImageView
    private lateinit var ivGps: ImageView
    private lateinit var searchResultsCard: View
    private lateinit var rvSearchResults: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnZoomIn: TextView
    private lateinit var btnZoomOut: TextView
    private lateinit var fabMyLocation: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var fabLayers: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var pbRouteLoading: ProgressBar
    private lateinit var loadingOverlay: View
    private lateinit var placeDetailsCard: View
    private lateinit var tvSelectedPlaceName: TextView
    private lateinit var btnGetDirections: Button
    private lateinit var routeDetailsCard: View
    private lateinit var tvRouteInfo: TextView
    private lateinit var btnStartJourney: Button

    // ── Map state ─────────────────────────────────────────────────────────
    private var mapplsMap: MapplsMap? = null
    private var currentLocation: ModelLatLng? = null
    private var locationMarker: Marker? = null
    private var searchAdapter = SearchResultsAdapter { result ->
        handleSearchResultSelected(result)
    }
    private var sourceLocation: ModelLatLng? = null
    private var destinationLocation: ModelLatLng? = null
    private var currentRouteInfo: com.chalsmooth.roadclassifier2.model.Route? = null
    private var isInitialCameraPositionSet = false

    // ── Services ──────────────────────────────────────────────────────────
    private val locationManager by lazy { LocationManager(this, this) }
    private var searchJob: Job? = null

    private val DEFAULT_ZOOM = 16.5

    // ══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            MapplsInitialiser.getInstance().initialise(applicationContext)
            Mappls.getInstance(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Mappls instance", e)
        }
        setContentView(R.layout.activity_map)

        initViews()
        setupClickListeners()

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        requestLocationPermission()

        mapView.postDelayed({ dismissLoadingOverlay() }, 6000L)
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)

        // Explore mode
        exploreSearchCard = findViewById(R.id.exploreSearchCard)
        etExploreSearch = findViewById(R.id.etExploreSearch)
        ivExploreClear = findViewById(R.id.ivExploreClear)
        ivNavigate = findViewById(R.id.ivNavigate)

        // Routing mode
        routingCard = findViewById(R.id.routingCard)
        ivBackToExplore = findViewById(R.id.ivBackToExplore)
        etSource = findViewById(R.id.etSource)
        etDestination = findViewById(R.id.etDestination)
        ivClearSource = findViewById(R.id.ivClearSource)
        ivClearDestination = findViewById(R.id.ivClearDestination)
        ivGps = findViewById(R.id.ivGps)

        // Search results
        searchResultsCard = findViewById(R.id.searchResultsCard)
        rvSearchResults = findViewById(R.id.rvSearchResults)

        // Misc
        fabMyLocation = findViewById(R.id.fabMyLocation)
        fabLayers = findViewById(R.id.fabLayers)
        pbRouteLoading = findViewById(R.id.pbRouteLoading)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        placeDetailsCard = findViewById(R.id.placeDetailsCard)
        tvSelectedPlaceName = findViewById(R.id.tvSelectedPlaceName)
        btnGetDirections = findViewById(R.id.btnGetDirections)
        routeDetailsCard = findViewById(R.id.routeDetailsCard)
        tvRouteInfo = findViewById(R.id.tvRouteInfo)
        btnStartJourney = findViewById(R.id.btnStartJourney)
    }

    private fun setupClickListeners() {
        // Explore search
        etExploreSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (etExploreSearch.text.length >= 3) debounceSearch(etExploreSearch.text.toString())
            }
        }

        etExploreSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ivExploreClear.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                if (etExploreSearch.hasFocus()) debounceSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etExploreSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                handleSearchSubmit(etExploreSearch.text.toString().trim())
                true
            } else false
        }

        ivExploreClear.setOnClickListener {
            etExploreSearch.setText("")
            hideSearchResults()
        }

        ivNavigate.setOnClickListener { switchToRoutingMode(fromPlaceDetails = false) }

        // Routing mode
        ivBackToExplore.setOnClickListener { switchToExploreMode() }
        ivGps.setOnClickListener { useCurrentLocationAsSource() }

        // Source/Destination fields
        setupRoutingSearch()

        // FABs
        fabMyLocation.setOnClickListener { centerOnCurrentLocation() }
        fabLayers.setOnClickListener {
            Toast.makeText(this@MapScreen, "Map layers coming soon", Toast.LENGTH_SHORT).show()
        }

        // Zoom controls
        btnZoomIn.setOnClickListener {
            mapplsMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        btnZoomIn.setOnLongClickListener {
            mapplsMap?.animateCamera(CameraUpdateFactory.zoomBy(2.0))
            true
        }
        btnZoomOut.setOnClickListener {
            mapplsMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }
        btnZoomOut.setOnLongClickListener {
            mapplsMap?.animateCamera(CameraUpdateFactory.zoomBy(-2.0))
            true
        }

        btnGetDirections.setOnClickListener { switchToRoutingMode(fromPlaceDetails = true) }
        btnStartJourney.setOnClickListener { Toast.makeText(this@MapScreen, "Journey started!", Toast.LENGTH_SHORT).show() }
        findViewById<View>(R.id.btnNavContribute).setOnClickListener {
            startActivity(android.content.Intent(this, ContributeActivity::class.java))
            overridePendingTransition(0, 0)
        }
    }

    private fun setupRoutingSearch() {
        // Source
        etSource.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (etSource.text.length >= 3) debounceSearch(etSource.text.toString())
            }
        }
        etSource.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ivClearSource.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                if (etSource.hasFocus()) debounceSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        etSource.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                handleSearchSubmit(etSource.text.toString().trim())
                true
            } else false
        }

        // Destination
        etDestination.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (etDestination.text.length >= 3) debounceSearch(etDestination.text.toString())
            }
        }
        etDestination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ivClearDestination.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                if (etDestination.hasFocus()) debounceSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        etDestination.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                handleSearchSubmit(etDestination.text.toString().trim())
                true
            } else false
        }

        // Clear buttons
        ivClearSource.setOnClickListener {
            etSource.setText("")
            hideSearchResults()
        }
        ivClearDestination.setOnClickListener {
            etDestination.setText("")
            hideSearchResults()
        }
    }

    private fun handleSearchSubmit(query: String) {
        if (query.length < 2) return
        debounceSearch(query)
    }

    private fun debounceSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(300L)
            if (query.length >= 3) {
                val results = MapplsSearchService.getInstance().searchAutoSuggest(query, currentLocation)
                if (results.isSuccess) {
                    val list = results.getOrNull()
                    if (!list.isNullOrEmpty()) {
                        searchResultsCard.visibility = View.VISIBLE
                        searchAdapter.updateResults(list)
                        if (rvSearchResults.adapter == null) {
                            rvSearchResults.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MapScreen)
                            rvSearchResults.adapter = searchAdapter
                        }
                    } else {
                        hideSearchResults()
                    }
                } else {
                    hideSearchResults()
                }
            } else {
                hideSearchResults()
            }
        }
    }

    private fun handleSearchResultSelected(result: com.chalsmooth.roadclassifier2.model.GeocodingResult) {
        hideSearchResults()
        hideKeyboard()
        
        etExploreSearch.clearFocus()
        etSource.clearFocus()
        etDestination.clearFocus()
        
        if (exploreSearchCard.visibility == View.VISIBLE) {
            etExploreSearch.setText(result.displayName)
            destinationLocation = result.coordinate
            mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(result.coordinate.latitude, result.coordinate.longitude), DEFAULT_ZOOM
            ))
            
            mapplsMap?.addMarker(MarkerOptions()
                .position(LatLng(result.coordinate.latitude, result.coordinate.longitude))
                .title(result.displayName))
                
            tvSelectedPlaceName.text = result.displayName
            placeDetailsCard.visibility = View.VISIBLE
        } else if (routingCard.visibility == View.VISIBLE) {
            if (etSource.hasFocus()) {
                etSource.setText(result.displayName)
                sourceLocation = result.coordinate
            } else if (etDestination.hasFocus()) {
                etDestination.setText(result.displayName)
                destinationLocation = result.coordinate
            }
            
            if (sourceLocation != null && destinationLocation != null) {
                fetchDirections()
            }
        }
    }

    private fun fetchDirections() {
        val src = sourceLocation ?: return
        val dest = destinationLocation ?: return
        pbRouteLoading.visibility = View.VISIBLE
        routeDetailsCard.visibility = View.GONE
        
        lifecycleScope.launch {
            val result = MapplsSearchService.getInstance().getRoute(src, dest)
            pbRouteLoading.visibility = View.GONE
            
            if (result.isSuccess) {
                val routes = result.getOrNull()
                if (!routes.isNullOrEmpty()) {
                    currentRouteInfo = routes.first()
                    drawRoute(currentRouteInfo!!)
                    tvRouteInfo.text = "Distance: ${currentRouteInfo!!.getFormattedDistance()} • Duration: ${currentRouteInfo!!.getFormattedDuration()}"
                    routeDetailsCard.visibility = View.VISIBLE
                } else {
                    Toast.makeText(this@MapScreen, "No routes found", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@MapScreen, "Failed to get directions", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var routePolyline: com.mappls.sdk.maps.annotations.Polyline? = null
    
    private fun drawRoute(route: com.chalsmooth.roadclassifier2.model.Route) {
        routePolyline?.remove()
        
        val points = route.coordinates.map { LatLng(it.latitude, it.longitude) }
        val polylineOptions = com.mappls.sdk.maps.annotations.PolylineOptions()
            .addAll(points)
            .color(Color.BLUE)
            .width(5f)
            
        routePolyline = mapplsMap?.addPolyline(polylineOptions)
        
        if (points.isNotEmpty()) {
            val bounds = com.mappls.sdk.maps.geometry.LatLngBounds.Builder().includes(points).build()
            mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        }
    }

    private fun hideSearchResults() {
        searchResultsCard.visibility = View.GONE
    }

    private fun switchToExploreMode() {
        exploreSearchCard.visibility = View.VISIBLE
        routingCard.visibility = View.GONE
        routeDetailsCard.visibility = View.GONE
        placeDetailsCard.visibility = View.GONE
        routePolyline?.remove()
        hideSearchResults()
        hideKeyboard()
    }

    private fun switchToRoutingMode(fromPlaceDetails: Boolean = false) {
        exploreSearchCard.visibility = View.GONE
        routingCard.visibility = View.VISIBLE
        placeDetailsCard.visibility = View.GONE
        
        if (fromPlaceDetails && destinationLocation != null) {
            etDestination.setText(tvSelectedPlaceName.text)
        }
        
        useCurrentLocationAsSource()
        hideSearchResults()
        
        if (etSource.text.isEmpty()) {
            etSource.requestFocus()
        } else {
            hideKeyboard()
        }
    }

    private fun useCurrentLocationAsSource() {
        currentLocation?.let { location ->
            etSource.setText("Current Location")
            sourceLocation = location
            hideSearchResults()
            if (destinationLocation != null && etDestination.text.isNotEmpty()) {
                fetchDirections()
            }
        } ?: run {
            Toast.makeText(this, "Acquiring GPS location…", Toast.LENGTH_SHORT).show()
            etSource.setText("")
            sourceLocation = null
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        val view = currentFocus ?: mapView
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun requestLocationPermission() {
        locationManager.requestLocationPermission(object : LocationManager.LocationPermissionCallback {
            override fun onPermissionGranted() {
                locationManager.startLocationUpdates()
                observeLocation()
                getCurrentLocation()
            }

            override fun onPermissionDenied() {
                Toast.makeText(this@MapScreen, R.string.location_permission_required, Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationManager.startLocationUpdates()
            observeLocation()
            getCurrentLocation()
        }
    }

    private fun observeLocation() {
        lifecycleScope.launch {
            locationManager.getLocationChannel().consumeEach<ModelLatLng> { location ->
                currentLocation = location
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    updateLocationMarker(location)
                    if (!isInitialCameraPositionSet) {
                        centerOnCurrentLocation(onFinished = ::dismissLoadingOverlay)
                        isInitialCameraPositionSet = true
                    }
                    fabMyLocation.visibility = View.VISIBLE
                    fabLayers.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun getCurrentLocation() {
        val location = locationManager.getLastKnownLocation()
        if (location != null) {
            currentLocation = location
            updateLocationMarker(location)
            if (!isInitialCameraPositionSet) {
                centerOnCurrentLocation(onFinished = ::dismissLoadingOverlay)
                isInitialCameraPositionSet = true
            }
        }
    }

    private fun updateLocationMarker(location: ModelLatLng) {
        locationMarker?.remove()
        val markerOptions = MarkerOptions()
            .position(LatLng(location.latitude, location.longitude))
            .title("Current Location")
            .icon(IconFactory.getInstance(this).fromBitmap(createLocationMarkerIcon()))
        locationMarker = mapplsMap?.addMarker(markerOptions)
    }

    private fun createLocationMarkerIcon(): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = true }
        paint.color = Color.parseColor("#1976D2")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f * 0.6f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f * 0.3f, paint)
        return bitmap
    }

    private fun centerOnCurrentLocation(onFinished: (() -> Unit)? = null) {
        currentLocation?.let { location ->
            mapplsMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    DEFAULT_ZOOM
                ),
                object : MapplsMap.CancelableCallback {
                    override fun onFinish() { onFinished?.invoke() }
                    override fun onCancel() { onFinished?.invoke() }
                }
            )
        } ?: run {
            onFinished?.invoke()
        }
    }

    private fun dismissLoadingOverlay() {
        if (loadingOverlay.visibility != View.VISIBLE) return
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(350)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
            }
            .start()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Mappls callbacks
    // ══════════════════════════════════════════════════════════════════════

    override fun onMapReady(mapplsMap: MapplsMap) {
        this.mapplsMap = mapplsMap

        // Enable ALL gestures - this is the key for map dragging & pinch zoom
        val uiSettings = mapplsMap.uiSettings
        if (uiSettings != null) {
            uiSettings.isZoomGesturesEnabled = true
            uiSettings.isScrollGesturesEnabled = true
            uiSettings.isRotateGesturesEnabled = true
            uiSettings.isTiltGesturesEnabled = true
            uiSettings.isDoubleTapGesturesEnabled = true
            Log.d(TAG, "Gestures enabled: zoom=${uiSettings.isZoomGesturesEnabled}, scroll=${uiSettings.isScrollGesturesEnabled}")
        } else {
            Log.e(TAG, "uiSettings is null!")
        }

        // No location component - we use our own marker
        currentLocation?.let {
            if (!isInitialCameraPositionSet) {
                centerOnCurrentLocation(onFinished = ::dismissLoadingOverlay)
                isInitialCameraPositionSet = true
            }
        }
    }

    override fun onMapError(code: Int, message: String?) {
        Log.e(TAG, "Map error: $code - $message")
        Toast.makeText(this@MapScreen, "Map load failed: $message", Toast.LENGTH_LONG).show()
    }

    // ══════════════════════════════════════════════════════════════════════
    // Activity lifecycle
    // ══════════════════════════════════════════════════════════════════════

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Re-establish location on resume
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationManager.startLocationUpdates()
            observeLocation()
            getCurrentLocation()
        }
    }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        super.onDestroy(); mapView.onDestroy()
        locationManager.stopLocationUpdates(); searchJob?.cancel()
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }

    companion object {
        private const val TAG = "MapScreen"
    }
}