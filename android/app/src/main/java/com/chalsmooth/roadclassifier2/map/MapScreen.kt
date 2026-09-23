package com.chalsmooth.roadclassifier2.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chalsmooth.roadclassifier2.R
import com.chalsmooth.roadclassifier2.geocoding.GeocodingService
import com.chalsmooth.roadclassifier2.location.LocationManager
import com.chalsmooth.roadclassifier2.model.LatLng as ModelLatLng
import com.chalsmooth.roadclassifier2.model.Route
import com.mappls.sdk.core.MapplsInitialiser
import com.mappls.sdk.maps.Mappls
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.geometry.LatLng
import com.mappls.sdk.maps.geometry.LatLngBounds
import com.mappls.sdk.maps.annotations.Marker
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.annotations.Polyline
import com.mappls.sdk.maps.annotations.PolylineOptions
import com.mappls.sdk.maps.annotations.IconFactory
import com.mappls.sdk.maps.location.LocationComponentActivationOptions
import com.mappls.sdk.maps.location.modes.CameraMode
import com.mappls.sdk.maps.location.modes.RenderMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

class MapScreen : AppCompatActivity(), OnMapReadyCallback {

    private enum class SearchTarget {
        NONE, SOURCE, DESTINATION
    }

    private lateinit var mapView: MapView
    private lateinit var etSource: EditText
    private lateinit var etDestination: EditText
    private lateinit var ivClearSource: ImageView
    private lateinit var ivClearDestination: ImageView
    private lateinit var ivGps: ImageView
    private lateinit var searchResultsCard: View
    private lateinit var rvSearchResults: androidx.recyclerview.widget.RecyclerView
    private lateinit var bottomSheetCard: View
    private lateinit var bottomSheetContainer: View
    private lateinit var rvRouteOptions: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnStartNavigation: Button
    private lateinit var fabMyLocation: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var fabLayers: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var pbRouteLoading: ProgressBar

    private var mapplsMap: MapplsMap? = null
    private var routePolylines = mutableMapOf<String, Polyline>()
    private var sourceMarker: Marker? = null
    private var destinationMarker: Marker? = null

    private val locationManager by lazy { LocationManager(this, this) }
    private val geocodingService = GeocodingService.getInstance()
    private val mapplsSearchService = MapplsSearchService.getInstance()

    private var currentLocation: ModelLatLng? = null
    private var sourceLocation: ModelLatLng? = null
    private var sourceName: String? = null
    private var destinationLocation: ModelLatLng? = null
    private var destinationName: String? = null
    private var currentRoutes = emptyList<Route>()
    private var selectedRouteId: String? = null

    private var activeSearchTarget = SearchTarget.NONE
    private var isInitialCameraPositionSet = false

    private val searchResultsAdapter = SearchResultsAdapter { result ->
        onSearchResultSelected(result)
    }
    private val routeOptionsAdapter = RouteOptionsAdapter({ route ->
        onRouteSelected(route)
    })

    private var searchJob: Job? = null
    private val DEBOUNCE_DELAY_MS = 300L
    private val DEFAULT_ZOOM = 16.5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            MapplsInitialiser.getInstance().initialise(applicationContext)
            Mappls.getInstance(applicationContext)
        } catch (e: Exception) {
            Log.e("MapScreen", "Failed to initialize Mappls instance in onCreate", e)
        }
        setContentView(R.layout.activity_map)

        initViews()
        setupSearch()
        setupRecyclerViews()
        setupClickListeners()
        setupBottomSheet()

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        requestLocationPermission()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        etSource = findViewById(R.id.etSource)
        etDestination = findViewById(R.id.etDestination)
        ivClearSource = findViewById(R.id.ivClearSource)
        ivClearDestination = findViewById(R.id.ivClearDestination)
        ivGps = findViewById(R.id.ivGps)
        searchResultsCard = findViewById(R.id.searchResultsCard)
        rvSearchResults = findViewById(R.id.rvSearchResults)
        bottomSheetCard = findViewById(R.id.bottomSheetCard)
        bottomSheetContainer = findViewById(R.id.bottomSheetContainer)
        rvRouteOptions = findViewById(R.id.rvRouteOptions)
        btnStartNavigation = findViewById(R.id.btnStartNavigation)
        fabMyLocation = findViewById(R.id.fabMyLocation)
        fabLayers = findViewById(R.id.fabLayers)
        pbRouteLoading = findViewById(R.id.pbRouteLoading)
    }

    private fun setupSearch() {
        // Source EditText focus, text, and keyboard enter listeners
        etSource.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activeSearchTarget = SearchTarget.SOURCE
                if (etSource.text.length >= 3) {
                    debounceSearch(etSource.text.toString())
                }
            }
        }

        etSource.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ivClearSource.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                if (etSource.hasFocus()) {
                    activeSearchTarget = SearchTarget.SOURCE
                    debounceSearch(s.toString())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etSource.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                handleSearchSubmit(SearchTarget.SOURCE, etSource.text.toString().trim())
                true
            } else {
                false
            }
        }

        // Destination EditText focus, text, and keyboard enter listeners
        etDestination.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activeSearchTarget = SearchTarget.DESTINATION
                if (etDestination.text.length >= 3) {
                    debounceSearch(etDestination.text.toString())
                }
            }
        }

        etDestination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ivClearDestination.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                if (etDestination.hasFocus()) {
                    activeSearchTarget = SearchTarget.DESTINATION
                    debounceSearch(s.toString())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etDestination.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                handleSearchSubmit(SearchTarget.DESTINATION, etDestination.text.toString().trim())
                true
            } else {
                false
            }
        }

        ivClearSource.setOnClickListener {
            etSource.setText("")
            sourceLocation = null
            sourceName = null
            updateSourceMarker(null)
            hideSearchResults()
            clearRoutes()
        }

        ivClearDestination.setOnClickListener {
            etDestination.setText("")
            destinationLocation = null
            destinationName = null
            updateDestinationMarker(null)
            hideSearchResults()
            clearRoutes()
        }
    }

    private fun handleSearchSubmit(target: SearchTarget, query: String) {
        hideKeyboard()
        hideSearchResults()
        if (query.length < 2) return

        activeSearchTarget = target
        showLoading(true)

        lifecycleScope.launch {
            val focusLoc = if (target == SearchTarget.DESTINATION) {
                sourceLocation ?: currentLocation
            } else {
                currentLocation
            }
            val result = mapplsSearchService.searchAutoSuggest(query, focusLoc)
            runOnUiThread {
                showLoading(false)
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.isSuccess) {
                    val data = result.getOrNull()
                    if (!data.isNullOrEmpty()) {
                        onSearchResultSelected(data.first())
                    } else {
                        Toast.makeText(this@MapScreen, "No results found for '$query'", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("MapScreen", "Search submit error", result.exceptionOrNull())
                    Toast.makeText(this@MapScreen, "Search failed for '$query'", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val view = currentFocus ?: mapView
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setupRecyclerViews() {
        rvSearchResults.layoutManager = LinearLayoutManager(this)
        rvSearchResults.adapter = searchResultsAdapter

        rvRouteOptions.layoutManager = LinearLayoutManager(this)
        rvRouteOptions.adapter = routeOptionsAdapter
    }

    private fun setupClickListeners() {
        ivGps.setOnClickListener {
            useCurrentLocationAsSource()
        }

        fabMyLocation.setOnClickListener {
            centerOnCurrentLocation()
        }

        fabLayers.setOnClickListener {
            Toast.makeText(this@MapScreen, "Map layers coming soon", Toast.LENGTH_SHORT).show()
        }

        btnStartNavigation.setOnClickListener {
            startNavigation()
        }
    }

    private fun setupBottomSheet() {
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetContainer)
        behavior.isHideable = true
        behavior.peekHeight = 120
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
    }

    private fun useCurrentLocationAsSource() {
        currentLocation?.let { location ->
            sourceLocation = location
            sourceName = "Current Location"
            updateSourceField(location)
            updateSourceMarker(location)
            centerOnCurrentLocation()
            hideSearchResults()

            if (destinationLocation != null) {
                requestRoutes()
            }
        } ?: run {
            Toast.makeText(this, "Acquiring GPS location...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun debounceSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(DEBOUNCE_DELAY_MS)
            if (query.length >= 3 && activeSearchTarget != SearchTarget.NONE) {
                performSearch(query)
            } else {
                hideSearchResults()
            }
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            val focusLoc = if (activeSearchTarget == SearchTarget.DESTINATION) {
                sourceLocation ?: currentLocation
            } else {
                currentLocation
            }

            val result = mapplsSearchService.searchAutoSuggest(query, focusLoc)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.isSuccess) {
                    val data = result.getOrNull()
                    if (!data.isNullOrEmpty()) {
                        searchResultsAdapter.updateResults(data)
                        showSearchResults()
                    } else {
                        hideSearchResults()
                    }
                } else {
                    Log.e("MapScreen", "AutoSuggest Search failed", result.exceptionOrNull())
                    hideSearchResults()
                }
            }
        }
    }

    private fun onSearchResultSelected(result: com.chalsmooth.roadclassifier2.model.GeocodingResult) {
        when (activeSearchTarget) {
            SearchTarget.SOURCE -> {
                sourceLocation = result.coordinate
                sourceName = result.displayName
                etSource.setText(result.displayName.split(",").first())
                ivClearSource.visibility = View.VISIBLE
                updateSourceMarker(result.coordinate)
                etSource.clearFocus()
            }
            SearchTarget.DESTINATION -> {
                destinationLocation = result.coordinate
                destinationName = result.displayName
                etDestination.setText(result.displayName.split(",").first())
                ivClearDestination.visibility = View.VISIBLE
                updateDestinationMarker(result.coordinate)
                etDestination.clearFocus()
            }
            SearchTarget.NONE -> {}
        }

        hideSearchResults()
        hideKeyboard()
        activeSearchTarget = SearchTarget.NONE

        if (sourceLocation != null && destinationLocation != null) {
            requestRoutes()
        }
    }

    private fun showSearchResults() {
        searchResultsCard.visibility = View.VISIBLE
    }

    private fun hideSearchResults() {
        searchResultsCard.visibility = View.GONE
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
    }

    private fun observeLocation() {
        lifecycleScope.launch {
            locationManager.getLocationChannel().consumeEach<ModelLatLng> { location ->
                currentLocation = location
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    if (sourceLocation == null) {
                        sourceLocation = location
                        sourceName = "Current Location"
                        updateSourceField(location)
                        updateSourceMarker(location)
                    }

                    if (!isInitialCameraPositionSet) {
                        centerOnCurrentLocation()
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
            if (sourceLocation == null) {
                sourceLocation = location
                sourceName = "Current Location"
                updateSourceField(location)
                updateSourceMarker(location)
            }
            if (!isInitialCameraPositionSet) {
                centerOnCurrentLocation()
                isInitialCameraPositionSet = true
            }
        }
    }

    private fun updateSourceField(location: ModelLatLng) {
        lifecycleScope.launch {
            val result = geocodingService.reverseGeocode(location)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.isSuccess) {
                    val name = result.getOrNull()?.displayName?.split(",")?.first() ?: "Current Location"
                    etSource.setText(name)
                    sourceName = name
                } else {
                    etSource.setText("Current Location")
                    sourceName = "Current Location"
                }
            }
        }
    }

    private fun centerOnCurrentLocation() {
        currentLocation?.let { location ->
            mapplsMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    DEFAULT_ZOOM
                )
            )
        }
    }

    private fun updateSourceMarker(location: ModelLatLng?) {
        sourceMarker?.remove()
        sourceMarker = null

        location?.let { loc ->
            val latLng = LatLng(loc.latitude, loc.longitude)
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title(sourceName ?: "Source Location")
                .icon(IconFactory.getInstance(this).fromBitmap(createSourceLocationIcon()))

            sourceMarker = mapplsMap?.addMarker(markerOptions)
        }
    }

    private fun updateDestinationMarker(location: ModelLatLng?) {
        destinationMarker?.remove()
        destinationMarker = null

        location?.let { loc ->
            val latLng = LatLng(loc.latitude, loc.longitude)
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title(destinationName ?: "Destination")
                .icon(IconFactory.getInstance(this).fromBitmap(createDestinationIcon()))
            destinationMarker = mapplsMap?.addMarker(markerOptions)
        }
    }

    private fun createSourceLocationIcon(): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = true }

        // White outer circle
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Green inner circle for source
        paint.color = Color.parseColor("#4CAF50")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f * 0.7f, paint)

        // White center dot
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f * 0.3f, paint)

        return bitmap
    }

    private fun createDestinationIcon(): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = true }

        paint.color = Color.parseColor("#F44336")

        val path = android.graphics.Path()
        val radius = 8f
        val top = 4f
        val bottom = size * 0.85f
        val left = size / 2f - 12f
        val right = size / 2f + 12f

        path.moveTo(size / 2f, bottom)
        path.lineTo(right, top + radius)
        path.quadTo(right, top, right - radius, top)
        path.quadTo(left, top, left, top + radius)
        path.close()

        canvas.drawPath(path, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f - 4f, 8f, paint)

        return bitmap
    }

    private fun requestRoutes() {
        val origin = sourceLocation ?: currentLocation
        val destination = destinationLocation

        if (origin == null || destination == null) return

        showLoading(true)
        lifecycleScope.launch {
            val result = mapplsSearchService.getRoute(origin, destination)
            runOnUiThread {
                showLoading(false)
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.isSuccess) {
                    val routes = result.getOrNull() ?: emptyList()
                    if (routes.isNotEmpty()) {
                        val routesWithRecommendation = routes.mapIndexed { index, route ->
                            route.copy(
                                isRecommended = index == 0,
                                comfortScore = (100.0 - index * 15.0).coerceAtLeast(20.0)
                            )
                        }
                        onRoutesReceived(routesWithRecommendation)
                    } else {
                        Toast.makeText(this@MapScreen, R.string.no_routes_found, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("MapScreen", "Routing failed", result.exceptionOrNull())
                    Toast.makeText(this@MapScreen, "Failed to calculate routes", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onRoutesReceived(routes: List<Route>) {
        if (routes.isEmpty()) return
        currentRoutes = routes
        routeOptionsAdapter.updateRoutes(routes)
        selectedRouteId = routeOptionsAdapter.getSelectedRoute()?.id

        renderRoutesOnMap(routes)
        updateSourceMarker(sourceLocation ?: currentLocation)
        updateDestinationMarker(destinationLocation)
        fitCameraToRoutes(routes)

        showBottomSheet()
    }

    private fun clearRoutes() {
        routePolylines.values.forEach { it.remove() }
        routePolylines.clear()
        currentRoutes = emptyList()
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetContainer)
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
        btnStartNavigation.visibility = View.GONE
    }

    private fun renderRoutesOnMap(routes: List<Route>) {
        routePolylines.values.forEach { it.remove() }
        routePolylines.clear()

        routes.forEachIndexed { _, route ->
            if (route.coordinates.isEmpty()) return@forEachIndexed

            val color = if (route.id == selectedRouteId) {
                route.getComfortColor()
            } else if (route.isRecommended) {
                0xFF4CAF50.toInt()
            } else {
                0xFF2196F3.toInt()
            }

            val width = if (route.id == selectedRouteId) 8f else 5f
            val latLngs = route.coordinates.map { LatLng(it.latitude, it.longitude) }

            val polylineOptions = PolylineOptions()
                .addAll(latLngs)
                .color(color)
                .width(width)

            try {
                val polyline = mapplsMap?.addPolyline(polylineOptions)
                polyline?.let { routePolylines[route.id] = it }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding polyline", e)
            }
        }
    }

    private fun updateSelectedRouteVisuals() {
        routePolylines.forEach { (routeId, polyline) ->
            val route = currentRoutes.firstOrNull { it.id == routeId }
            route?.let {
                val color = if (routeId == selectedRouteId) {
                    it.getComfortColor()
                } else if (it.isRecommended) {
                    0xFF4CAF50.toInt()
                } else {
                    0xFF2196F3.toInt()
                }
                val width = if (routeId == selectedRouteId) 8f else 5f
                polyline.color = color
                polyline.width = width
            }
        }
    }

    private fun fitCameraToRoutes(routes: List<Route>) {
        val points = mutableListOf<LatLng>()

        (sourceLocation ?: currentLocation)?.let { points.add(LatLng(it.latitude, it.longitude)) }
        destinationLocation?.let { points.add(LatLng(it.latitude, it.longitude)) }

        routes.forEach { route ->
            route.coordinates.forEach { points.add(LatLng(it.latitude, it.longitude)) }
        }

        if (points.isEmpty()) return

        var minLat = points[0].latitude
        var maxLat = points[0].latitude
        var minLng = points[0].longitude
        var maxLng = points[0].longitude

        for (p in points) {
            minLat = minOf(minLat, p.latitude)
            maxLat = maxOf(maxLat, p.latitude)
            minLng = minOf(minLng, p.longitude)
            maxLng = maxOf(maxLng, p.longitude)
        }

        if (minLat == maxLat && minLng == maxLng) {
            mapplsMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(minLat, minLng), DEFAULT_ZOOM)
            )
            return
        }

        try {
            val bounds = LatLngBounds.Builder()
                .include(LatLng(minLat, minLng))
                .include(LatLng(maxLat, maxLng))
                .build()

            mapplsMap?.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, 120, 120, 120, 120)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating camera bounds", e)
            mapplsMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(minLat, minLng), 14.0)
            )
        }
    }

    private fun onRouteSelected(route: Route) {
        selectedRouteId = route.id
        routeOptionsAdapter.setSelectedRoute(route.id)
        updateSelectedRouteVisuals()
    }

    private fun showBottomSheet() {
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetContainer)
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        btnStartNavigation.visibility = View.VISIBLE
    }

    private fun startNavigation() {
        selectedRouteId?.let { routeId ->
            val route = currentRoutes.firstOrNull { it.id == routeId }
            route?.let {
                Toast.makeText(this@MapScreen, "Navigation started for ${it.getFormattedDistance()}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        pbRouteLoading.visibility = if (show) View.VISIBLE else View.GONE
    }

    // Mappls OnMapReadyCallback
    override fun onMapReady(mapplsMap: MapplsMap) {
        this.mapplsMap = mapplsMap

        enableLocationComponent()

        currentLocation?.let {
            if (!isInitialCameraPositionSet) {
                centerOnCurrentLocation()
                isInitialCameraPositionSet = true
            }
            updateSourceMarker(sourceLocation ?: it)
        }
        destinationLocation?.let { updateDestinationMarker(it) }

        if (currentRoutes.isNotEmpty()) {
            renderRoutesOnMap(currentRoutes)
            fitCameraToRoutes(currentRoutes)
        }
    }

    override fun onMapError(code: Int, message: String?) {
        Log.e("MapScreen", "Map error: $code - $message")
        Toast.makeText(this@MapScreen, "Map load failed: $message", Toast.LENGTH_LONG).show()
    }

    private fun enableLocationComponent() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        mapplsMap?.let { map ->
            val locationComponent = map.locationComponent
            val activationOptions = LocationComponentActivationOptions
                .builder(this, map.style ?: return@let)
                .build()
            locationComponent.activateLocationComponent(activationOptions)
            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = CameraMode.TRACKING
            locationComponent.renderMode = RenderMode.COMPASS
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        locationManager.stopLocationUpdates()
        searchJob?.cancel()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    companion object {
        private const val TAG = "MapScreen"
    }
}