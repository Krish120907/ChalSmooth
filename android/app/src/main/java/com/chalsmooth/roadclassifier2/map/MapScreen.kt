package com.chalsmooth.roadclassifier2.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chalsmooth.roadclassifier2.R
import com.chalsmooth.roadclassifier2.geocoding.GeocodingService
import com.chalsmooth.roadclassifier2.location.LocationManager
import com.chalsmooth.roadclassifier2.model.LatLng as ModelLatLng
import com.chalsmooth.roadclassifier2.model.Route
import com.chalsmooth.roadclassifier2.routing.OsrmRoutingService
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.camera.CameraPosition
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

class MapScreen : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var etSource: EditText
    private lateinit var etDestination: EditText
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
    private var userLocationMarker: Marker? = null
    private var destinationMarker: Marker? = null

    private val locationManager by lazy { LocationManager(this, this) }
    private val geocodingService = GeocodingService.getInstance()
    private val routingService = OsrmRoutingService.getInstance()

    private var currentLocation: ModelLatLng? = null
    private var destinationLocation: ModelLatLng? = null
    private var destinationName: String? = null
    private var currentRoutes = emptyList<Route>()
    private var selectedRouteId: String? = null

    private val searchResultsAdapter = SearchResultsAdapter { result ->
        onSearchResultSelected(result)
    }
    private val routeOptionsAdapter = RouteOptionsAdapter({ route ->
        onRouteSelected(route)
    })

    private var searchJob: Job? = null
    private val DEBOUNCE_DELAY_MS = 300L

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        etDestination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ivClearDestination.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                debounceSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        ivClearDestination.setOnClickListener {
            etDestination.setText("")
            hideSearchResults()
        }
    }

    private fun setupRecyclerViews() {
        rvSearchResults.layoutManager = LinearLayoutManager(this)
        rvSearchResults.adapter = searchResultsAdapter

        rvRouteOptions.layoutManager = LinearLayoutManager(this)
        rvRouteOptions.adapter = routeOptionsAdapter
    }

    private fun setupClickListeners() {
        ivGps.setOnClickListener {
            centerOnCurrentLocation()
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

    private fun debounceSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(DEBOUNCE_DELAY_MS)
            if (query.length >= 3) {
                performSearch(query)
            } else {
                hideSearchResults()
            }
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            val result = geocodingService.search(query)
            runOnUiThread {
                if (result.isSuccess) {
                    val data = result.getOrNull()
                    if (data?.isNotEmpty() == true) {
                        searchResultsAdapter.updateResults(data)
                        showSearchResults()
                    } else {
                        hideSearchResults()
                    }
                } else {
                    Log.e("MapScreen", "Search failed", result.exceptionOrNull())
                    hideSearchResults()
                }
            }
        }
    }

    private fun onSearchResultSelected(result: com.chalsmooth.roadclassifier2.model.GeocodingResult) {
        destinationLocation = result.coordinate
        destinationName = result.displayName
        etDestination.setText(result.displayName.split(",").first())
        hideSearchResults()
        ivClearDestination.visibility = View.VISIBLE

        if (currentLocation != null) {
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
                    updateSourceField(location)
                    updateUserLocationMarker(location)
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
            updateSourceField(location)
            updateUserLocationMarker(location)
            centerOnCurrentLocation()
        }
    }

    private fun updateSourceField(location: ModelLatLng) {
        lifecycleScope.launch {
            val result = geocodingService.reverseGeocode(location)
            runOnUiThread {
                if (result.isSuccess) {
                    etSource.setText(result.getOrNull()?.displayName?.split(",")?.first() ?: "Current Location")
                } else {
                    etSource.setText("Current Location")
                }
            }
        }
    }

    private fun centerOnCurrentLocation() {
        currentLocation?.let { location ->
            mapplsMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    15.0
                )
            )
        }
    }

    private fun updateUserLocationMarker(location: ModelLatLng) {
        val latLng = LatLng(location.latitude, location.longitude)
        
        userLocationMarker?.remove()
        
        val markerOptions = MarkerOptions()
            .position(latLng)
            .title("Your Location")
            .icon(IconFactory.getInstance(this).fromBitmap(createUserLocationIcon()))
        
        userLocationMarker = mapplsMap?.addMarker(markerOptions)
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

    private fun createUserLocationIcon(): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = true }

        // White outer circle
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Blue inner circle
        paint.color = Color.parseColor("#2196F3")
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
        currentLocation?.let { origin ->
            destinationLocation?.let { destination ->
                showLoading(true)
                lifecycleScope.launch {
                    val result = routingService.getRoutes(origin, destination)
                    runOnUiThread {
                        showLoading(false)
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
        }
    }

    private fun onRoutesReceived(routes: List<Route>) {
        currentRoutes = routes
        routeOptionsAdapter.updateRoutes(routes)
        selectedRouteId = routeOptionsAdapter.getSelectedRoute()?.id

        renderRoutesOnMap(routes)
        updateDestinationMarker(destinationLocation)
        fitCameraToRoutes(routes)

        showBottomSheet()
    }

    private fun renderRoutesOnMap(routes: List<Route>) {
        // Clear existing polylines
        routePolylines.values.forEach { it.remove() }
        routePolylines.clear()

        routes.forEachIndexed { index, route ->
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

            val polyline = mapplsMap?.addPolyline(polylineOptions)
            polyline?.let { routePolylines[route.id] = it }
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
        if (routes.isEmpty()) return

        val allCoords = routes.flatMap { it.coordinates }
        if (allCoords.isEmpty()) return

        var minLat = allCoords[0].latitude
        var maxLat = allCoords[0].latitude
        var minLng = allCoords[0].longitude
        var maxLng = allCoords[0].longitude

        for (coord in allCoords) {
            minLat = minOf(minLat, coord.latitude)
            maxLat = maxOf(maxLat, coord.latitude)
            minLng = minOf(minLng, coord.longitude)
            maxLng = maxOf(maxLng, coord.longitude)
        }

        val bounds = LatLngBounds.Builder()
            .include(LatLng(minLat, minLng))
            .include(LatLng(maxLat, maxLng))
            .build()

        // Use padding for bounds - left, top, right, bottom
        mapplsMap?.moveCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, 100, 100, 100, 100)
        )
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
        
        // Enable location component for user location
        enableLocationComponent()
        
        // Restore markers if locations already available
        currentLocation?.let { updateUserLocationMarker(it) }
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