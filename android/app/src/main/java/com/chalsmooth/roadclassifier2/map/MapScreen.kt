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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chalsmooth.roadclassifier2.data.PotholeDatabaseHelper
import com.chalsmooth.roadclassifier2.R
import com.chalsmooth.roadclassifier2.location.LocationManager
import com.chalsmooth.roadclassifier2.model.GeocodingResult
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.mappls.sdk.services.api.autosuggest.MapplsAutoSuggest
import com.mappls.sdk.services.api.autosuggest.MapplsAutosuggestManager
import com.mappls.sdk.services.api.autosuggest.model.AutoSuggestAtlasResponse
import com.mappls.sdk.services.api.OnResponseCallback

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
    private lateinit var exploreDivider: View
    private lateinit var rvExploreSearchResults: androidx.recyclerview.widget.RecyclerView
    private lateinit var rvQuickCategories: androidx.recyclerview.widget.RecyclerView
    private lateinit var routingDivider: View
    private lateinit var rvRoutingSearchResults: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnZoomIn: TextView
    private lateinit var btnZoomOut: TextView
    private lateinit var zoomControls: LinearLayout
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
    private var searchAdapter = SearchResultsAdapter(
        onItemClick = { result -> handleSearchResultSelected(result) },
        onHistoryClick = { historyItem -> handleHistoryItemSelected(historyItem) }
    )
    private var quickCategoriesAdapter: QuickCategoriesAdapter? = null
    private var sourceLocation: ModelLatLng? = null
    private var destinationLocation: ModelLatLng? = null
    private var currentRouteInfo: com.chalsmooth.roadclassifier2.model.Route? = null
    private var isInitialCameraPositionSet = false
    private val potholeMarkers = ArrayList<Marker>()
    private val categoryMarkers = ArrayList<Marker>()

    // ── Services ──────────────────────────────────────────────────────────
    private val locationManager by lazy { LocationManager(this, this) }
    private val searchHistoryManager by lazy { SearchHistoryManager.getInstance(this) }
    private var searchJob: Job? = null
    private var categorySearchJob: Job? = null

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
        setupBackPressHandler()

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

        // Search results (merged inside respective cards)
        exploreDivider = findViewById(R.id.exploreDivider)
        rvExploreSearchResults = findViewById(R.id.rvExploreSearchResults)
        rvQuickCategories = findViewById(R.id.rvQuickCategories)
        routingDivider = findViewById(R.id.routingDivider)
        rvRoutingSearchResults = findViewById(R.id.rvRoutingSearchResults)

        // Setup quick categories
        setupQuickCategories()

        // Misc
        fabMyLocation = findViewById(R.id.fabMyLocation)
        fabLayers = findViewById(R.id.fabLayers)
        zoomControls = findViewById(R.id.zoomControls)
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

    private fun setupQuickCategories() {
        val categories = listOf(
            QuickCategory("Petrol", R.drawable.ic_petrol, "PETROL_PUMP"),
            QuickCategory("Restaurant", R.drawable.ic_restaurant, "RESTAURANT"),
            QuickCategory("Park", R.drawable.ic_park, "PARK"),
            QuickCategory("Hospital", R.drawable.ic_hospital, "HOSPITAL"),
            QuickCategory("ATM", R.drawable.ic_atm, "ATM"),
            QuickCategory("Cafe", R.drawable.ic_cafe, "CAFE"),
            QuickCategory("Pharmacy", R.drawable.ic_pharmacy, "PHARMACY")
        )
        quickCategoriesAdapter = QuickCategoriesAdapter(categories) { category ->
            searchNearbyByCategory(category)
        }
        rvQuickCategories.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        rvQuickCategories.addItemDecoration(object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
                outRect.right = resources.getDimensionPixelSize(R.dimen.category_spacing)
            }
        })
        rvQuickCategories.adapter = quickCategoriesAdapter
    }

    private fun setupClickListeners() {
        // Explore search
        etExploreSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val query = etExploreSearch.text.toString()
                if (query.length >= 3) {
                    debounceSearch(query)
                } else {
                    showSearchHistory()
                }
            }
        }

        etExploreSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ivExploreClear.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                if (etExploreSearch.hasFocus()) {
                    val query = s.toString()
                    if (query.length >= 3) {
                        debounceSearch(query)
                    } else {
                        showSearchHistory()
                    }
                }
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
            showServerConfigDialog()
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
                val query = etSource.text.toString()
                if (query.length >= 3) {
                    debounceSearch(query)
                } else {
                    showSearchHistory()
                }
            }
        }
        etSource.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ivClearSource.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                if (etSource.hasFocus()) {
                    val query = s.toString()
                    if (query.length >= 3) {
                        debounceSearch(query)
                    } else {
                        showSearchHistory()
                    }
                }
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
                val query = etDestination.text.toString()
                if (query.length >= 3) {
                    debounceSearch(query)
                } else {
                    showSearchHistory()
                }
            }
        }
        etDestination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ivClearDestination.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                if (etDestination.hasFocus()) {
                    val query = s.toString()
                    if (query.length >= 3) {
                        debounceSearch(query)
                    } else {
                        showSearchHistory()
                    }
                }
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
                        showSearchResults(list, emptyList())
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

    private fun searchNearbyByCategory(category: QuickCategory) {
        currentLocation ?: return
        
        categorySearchJob?.cancel()
        categorySearchJob = lifecycleScope.launch {
            try {
                val list = searchMapplsNearby(category.mapplsCategory, currentLocation!!)
                // Filter to 2km radius
                val nearby = list.filter { result ->
                    result.coordinate?.let { coord ->
                        calculateDistance(currentLocation!!, coord) <= 2000.0
                    } ?: false
                }
                if (!nearby.isNullOrEmpty()) {
                    showCategoryResultsOnMap(nearby)
                } else {
                    Toast.makeText(this@MapScreen, "No ${category.name.toLowerCase()}s found within 2km", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MapScreen, "Failed to search nearby ${category.name.toLowerCase()}s", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun searchMapplsNearby(category: String, location: ModelLatLng): List<GeocodingResult> {
        return kotlin.coroutines.suspendCoroutine { continuation ->
            try {
                val builder = MapplsAutoSuggest.builder()
                    .query(category)
                    .setLocation(location.latitude, location.longitude)
                    .build()

                MapplsAutosuggestManager.newInstance(builder).call(object : OnResponseCallback<AutoSuggestAtlasResponse> {
                    override fun onSuccess(response: AutoSuggestAtlasResponse?) {
                        val suggestions = response?.suggestedLocations ?: emptyList()
                        val results = suggestions.mapNotNull { suggestion ->
                            val lat = suggestion.latitude
                            val lng = suggestion.longitude
                            if (lat != null && lng != null) {
                                GeocodingResult(
                                    placeId = suggestion.mapplsPin ?: suggestion.placeAddress ?: "",
                                    displayName = suggestion.placeName ?: suggestion.placeAddress ?: category,
                                    coordinate = ModelLatLng(lat, lng),
                                    addressType = suggestion.type ?: category.toLowerCase(),
                                    importance = 1.0
                                )
                            } else null
                        }
                        if (results.isNotEmpty()) {
                            continuation.resume(results)
                        } else {
                            continuation.resumeWithException(Exception("No nearby $category found"))
                        }
                    }

                    override fun onError(code: Int, message: String?) {
                        continuation.resumeWithException(Exception("Mappls nearby search error $code: $message"))
                    }
                })
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    private fun showCategoryResultsOnMap(results: List<GeocodingResult>) {
        // Clear previous category markers
        categoryMarkers.forEach { it.remove() }
        categoryMarkers.clear()

        val iconFactory = IconFactory.getInstance(this)
        val icon = iconFactory.fromBitmap(createCategoryMarkerIcon())

        for (result in results) {
            val markerOptions = MarkerOptions()
                .position(com.mappls.sdk.maps.geometry.LatLng(result.coordinate.latitude, result.coordinate.longitude))
                .title(result.displayName)
                .snippet(result.addressType)
                .icon(icon)

            val marker = mapplsMap?.addMarker(markerOptions)
            marker?.let { categoryMarkers.add(it) }
        }

        // Fit map to show all markers
        if (categoryMarkers.isNotEmpty()) {
            val boundsBuilder = com.mappls.sdk.maps.geometry.LatLngBounds.Builder()
            categoryMarkers.forEach { marker ->
                boundsBuilder.include(marker.position)
            }
            if (currentLocation != null) {
                boundsBuilder.include(com.mappls.sdk.maps.geometry.LatLng(currentLocation!!.latitude, currentLocation!!.longitude))
            }
            val bounds = boundsBuilder.build()
            mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        }

        Toast.makeText(this@MapScreen, "Found ${results.size} nearby places", Toast.LENGTH_SHORT).show()
    }

    private fun createCategoryMarkerIcon(): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = true }
        
        // Green marker for category places
        paint.color = Color.parseColor("#4CAF50")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f * 0.7f, paint)
        
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f * 0.35f, paint)
        
        return bitmap
    }

    private fun showSearchResults(results: List<GeocodingResult>, history: List<SearchHistoryManager.HistoryItem>) {
        val (divider, recyclerView) = when {
            exploreSearchCard.visibility == View.VISIBLE -> Pair(exploreDivider, rvExploreSearchResults)
            routingCard.visibility == View.VISIBLE -> Pair(routingDivider, rvRoutingSearchResults)
            else -> Pair(exploreDivider, rvExploreSearchResults)
        }

        if (recyclerView == rvExploreSearchResults) {
            if (::routingDivider.isInitialized) routingDivider.visibility = View.GONE
            if (::rvRoutingSearchResults.isInitialized) rvRoutingSearchResults.visibility = View.GONE
        } else {
            if (::exploreDivider.isInitialized) exploreDivider.visibility = View.GONE
            if (::rvExploreSearchResults.isInitialized) rvExploreSearchResults.visibility = View.GONE
        }

        divider.visibility = View.VISIBLE
        recyclerView.visibility = View.VISIBLE
        if (::rvQuickCategories.isInitialized) rvQuickCategories.visibility = View.GONE
        searchAdapter.updateResults(results, history)
        searchAdapter.updateLocation(currentLocation)
        if (recyclerView.adapter == null) {
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MapScreen)
            recyclerView.adapter = searchAdapter
        }
        setMapControlsVisibility(false)
    }

    private fun hideSearchResults() {
        if (::exploreDivider.isInitialized) exploreDivider.visibility = View.GONE
        if (::rvExploreSearchResults.isInitialized) rvExploreSearchResults.visibility = View.GONE
        if (::routingDivider.isInitialized) routingDivider.visibility = View.GONE
        if (::rvRoutingSearchResults.isInitialized) rvRoutingSearchResults.visibility = View.GONE
        if (::rvQuickCategories.isInitialized) rvQuickCategories.visibility = View.VISIBLE
        setMapControlsVisibility(true)
    }

    private fun setMapControlsVisibility(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        if (::fabMyLocation.isInitialized) fabMyLocation.visibility = visibility
        if (::fabLayers.isInitialized) fabLayers.visibility = visibility
        if (::btnZoomIn.isInitialized) btnZoomIn.visibility = visibility
        if (::btnZoomOut.isInitialized) btnZoomOut.visibility = visibility
        if (::zoomControls.isInitialized) zoomControls.visibility = visibility
    }

    private fun showSearchHistory() {
        val history = searchHistoryManager.getHistory()
        if (history.isNotEmpty()) {
            showSearchResults(emptyList(), history)
        } else {
            hideSearchResults()
        }
    }

    private fun handleSearchResultSelected(result: SearchItem) {
        when (result) {
            is SearchItem.Result -> {
                hideSearchResults()
                hideKeyboard()
                saveSearchToHistory(result.geocodingResult)
                
                etExploreSearch.clearFocus()
                etSource.clearFocus()
                etDestination.clearFocus()
                
                if (exploreSearchCard.visibility == View.VISIBLE) {
                    etExploreSearch.setText(result.geocodingResult.displayName)
                    destinationLocation = result.geocodingResult.coordinate
                    mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(result.geocodingResult.coordinate.latitude, result.geocodingResult.coordinate.longitude), DEFAULT_ZOOM
                    ))
                    
                    mapplsMap?.addMarker(MarkerOptions()
                        .position(LatLng(result.geocodingResult.coordinate.latitude, result.geocodingResult.coordinate.longitude))
                        .title(result.geocodingResult.displayName))
                        
                    tvSelectedPlaceName.text = result.geocodingResult.displayName
                    placeDetailsCard.visibility = View.VISIBLE
                } else if (routingCard.visibility == View.VISIBLE) {
                    if (etSource.hasFocus()) {
                        etSource.setText(result.geocodingResult.displayName)
                        sourceLocation = result.geocodingResult.coordinate
                    } else if (etDestination.hasFocus()) {
                        etDestination.setText(result.geocodingResult.displayName)
                        destinationLocation = result.geocodingResult.coordinate
                    }
                    
                    if (sourceLocation != null && destinationLocation != null) {
                        fetchDirections()
                    }
                }
            }
            is SearchItem.History -> {
                handleHistoryItemSelected(result.item)
            }
            is SearchItem.Header -> {
                // Do nothing for headers
            }
        }
    }

    private fun saveSearchToHistory(result: GeocodingResult) {
        searchHistoryManager.saveSearch(result)
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

    private fun handleHistoryItemSelected(item: SearchHistoryManager.HistoryItem) {
        hideSearchResults()
        hideKeyboard()
        
        etExploreSearch.clearFocus()
        etSource.clearFocus()
        etDestination.clearFocus()
        
        item.coordinate?.let { coordinate ->
            if (exploreSearchCard.visibility == View.VISIBLE) {
                etExploreSearch.setText(item.displayName)
                destinationLocation = coordinate
                mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(coordinate.latitude, coordinate.longitude), DEFAULT_ZOOM
                ))
                
                mapplsMap?.addMarker(MarkerOptions()
                    .position(LatLng(coordinate.latitude, coordinate.longitude))
                    .title(item.displayName))
                    
                tvSelectedPlaceName.text = item.displayName
                placeDetailsCard.visibility = View.VISIBLE
            } else if (routingCard.visibility == View.VISIBLE) {
                if (etSource.hasFocus()) {
                    etSource.setText(item.displayName)
                    sourceLocation = coordinate
                } else if (etDestination.hasFocus()) {
                    etDestination.setText(item.displayName)
                    destinationLocation = coordinate
                }
                
                if (sourceLocation != null && destinationLocation != null) {
                    fetchDirections()
                }
            }
        }
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

    private fun isSearchResultsVisible() =
        rvExploreSearchResults.visibility == View.VISIBLE ||
        rvRoutingSearchResults.visibility == View.VISIBLE

    private fun dismissSearchBar() {
        hideSearchResults()
        etExploreSearch.clearFocus()
        etSource.clearFocus()
        etDestination.clearFocus()
        hideKeyboard()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    // 1. Search suggestions/history are open — close them
                    isSearchResultsVisible() -> dismissSearchBar()
                    // 2. In routing mode — go back to explore mode
                    routingCard.visibility == View.VISIBLE -> switchToExploreMode()
                    // 3. Nothing special — close the activity normally
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
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
                    searchAdapter.updateLocation(location)
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
            searchAdapter.updateLocation(location)
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

        // Dismiss search when tapping the map
        mapplsMap.addOnMapClickListener { _ ->
            if (isSearchResultsVisible()) {
                dismissSearchBar()
                true
            } else {
                false
            }
        }

        loadAndDisplayPotholes()
    }

    private fun loadAndDisplayPotholes() {
        val map = mapplsMap ?: return

        potholeMarkers.forEach { it.remove() }
        potholeMarkers.clear()

        val dbHelper = PotholeDatabaseHelper(this)
        val potholes = dbHelper.getAllPotholes()

        val icon = IconFactory.getInstance(this).fromBitmap(createPotholeMarkerIcon())

        for (pothole in potholes) {
            val dateStr = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(pothole.timestamp))

            val markerOptions = MarkerOptions()
                .position(LatLng(pothole.latitude, pothole.longitude))
                .title("${pothole.label} (${(pothole.confidence * 100).toInt()}%)")
                .snippet("Reported: $dateStr")
                .icon(icon)

            val marker = map.addMarker(markerOptions)
            marker?.let { potholeMarkers.add(it) }
        }

        // Fetch nearby potholes around user's location from remote laptop server
        val loc = currentLocation ?: return
        lifecycleScope.launch {
            val client = com.chalsmooth.roadclassifier2.network.PotholeApiClient(this@MapScreen)
            val remotePotholes = client.fetchNearbyPotholes(loc.latitude, loc.longitude, radiusKm = 10.0)
            if (remotePotholes.isNotEmpty()) {
                var newCount = 0
                for (rp in remotePotholes) {
                    if (dbHelper.insertPothole(rp)) {
                        newCount++
                    }
                }
                if (newCount > 0) {
                    // Re-render markers with new server data
                    val updatedList = dbHelper.getAllPotholes()
                    potholeMarkers.forEach { it.remove() }
                    potholeMarkers.clear()
                    for (pothole in updatedList) {
                        val dateStr = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(pothole.timestamp))
                        val markerOptions = MarkerOptions()
                            .position(LatLng(pothole.latitude, pothole.longitude))
                            .title("${pothole.label} (${(pothole.confidence * 100).toInt()}%)")
                            .snippet("Reported: $dateStr")
                            .icon(icon)
                        val marker = map.addMarker(markerOptions)
                        marker?.let { potholeMarkers.add(it) }
                    }
                }
            }
        }
    }

    private fun createPotholeMarkerIcon(): Bitmap {
        val size = 52
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = true }

        // Red outer circle
        paint.color = Color.parseColor("#E53935")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // White inner circle
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f * 0.6f, paint)

        // Dark red center
        paint.color = Color.parseColor("#B71C1C")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f * 0.35f, paint)

        return bitmap
    }

    private fun showServerConfigDialog() {
        val client = com.chalsmooth.roadclassifier2.network.PotholeApiClient(this)
        val input = EditText(this).apply {
            setText(client.getServerBaseUrl())
            hint = "https://xxxx.ngrok-free.app"
            setPadding(40, 30, 40, 30)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Laptop Backend Server URL")
            .setMessage("Enter your laptop ngrok public URL:")
            .setView(input)
            .setPositiveButton("Save & Sync") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    client.setServerBaseUrl(url)
                    Toast.makeText(this, "Server URL updated!", Toast.LENGTH_SHORT).show()
                    loadAndDisplayPotholes()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        loadAndDisplayPotholes()
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
        categorySearchJob?.cancel()
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }

    private fun calculateDistance(loc1: ModelLatLng, loc2: ModelLatLng): Double {
        val R = 6371000.0 // Earth radius in meters
        val lat1 = Math.toRadians(loc1.latitude)
        val lat2 = Math.toRadians(loc2.latitude)
        val deltaLat = Math.toRadians(loc2.latitude - loc1.latitude)
        val deltaLng = Math.toRadians(loc2.longitude - loc1.longitude)

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    companion object {
        private const val TAG = "MapScreen"
    }
}