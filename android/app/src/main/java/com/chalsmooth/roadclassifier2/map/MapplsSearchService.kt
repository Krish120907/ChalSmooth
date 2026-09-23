package com.chalsmooth.roadclassifier2.map

import android.util.Log
import com.chalsmooth.roadclassifier2.geocoding.GeocodingService
import com.chalsmooth.roadclassifier2.model.GeocodingResult
import com.chalsmooth.roadclassifier2.model.LatLng
import com.chalsmooth.roadclassifier2.model.Route
import com.chalsmooth.roadclassifier2.routing.OsrmRoutingService
import com.mappls.sdk.services.api.OnResponseCallback
import com.mappls.sdk.services.api.autosuggest.MapplsAutoSuggest
import com.mappls.sdk.services.api.autosuggest.MapplsAutosuggestManager
import com.mappls.sdk.services.api.autosuggest.model.AutoSuggestAtlasResponse
import com.mappls.sdk.services.api.directions.DirectionsCriteria
import com.mappls.sdk.services.api.directions.MapplsDirectionManager
import com.mappls.sdk.services.api.directions.MapplsDirections
import com.mappls.sdk.services.api.directions.models.DirectionsResponse
import com.mappls.sdk.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MapplsSearchService private constructor() {

    private val geocodingService = GeocodingService.getInstance()
    private val osrmRoutingService = OsrmRoutingService.getInstance()

    suspend fun searchAutoSuggest(query: String, focusLocation: LatLng? = null): Result<List<GeocodingResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val mapplsResult = searchMapplsAutoSuggest(query, focusLocation)
                if (mapplsResult.isSuccess && !mapplsResult.getOrNull().isNullOrEmpty()) {
                    mapplsResult
                } else {
                    geocodingService.search(query)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mappls AutoSuggest failed, falling back to Nominatim", e)
                geocodingService.search(query)
            }
        }
    }

    private suspend fun searchMapplsAutoSuggest(query: String, focusLocation: LatLng?): Result<List<GeocodingResult>> {
        return kotlin.coroutines.suspendCoroutine { continuation ->
            try {
                val builder = MapplsAutoSuggest.builder()
                    .query(query)

                focusLocation?.let {
                    builder.setLocation(it.latitude, it.longitude)
                }

                val autoSuggest = builder.build()
                MapplsAutosuggestManager.newInstance(autoSuggest).call(object : OnResponseCallback<AutoSuggestAtlasResponse> {
                    override fun onSuccess(response: AutoSuggestAtlasResponse?) {
                        val suggestions = response?.suggestedLocations ?: emptyList()
                        val results = suggestions.mapNotNull { suggestion ->
                            val lat = suggestion.latitude
                            val lng = suggestion.longitude
                            if (lat != null && lng != null) {
                                GeocodingResult(
                                    placeId = suggestion.mapplsPin ?: suggestion.placeAddress ?: "",
                                    displayName = suggestion.placeName ?: suggestion.placeAddress ?: query,
                                    coordinate = LatLng(lat, lng),
                                    addressType = suggestion.type ?: "place",
                                    importance = 1.0
                                )
                            } else null
                        }
                        if (results.isNotEmpty()) {
                            continuation.resume(Result.success(results))
                        } else {
                            continuation.resume(Result.failure(Exception("No Mappls suggestions found")))
                        }
                    }

                    override fun onError(code: Int, message: String?) {
                        continuation.resume(Result.failure(Exception("Mappls search error $code: $message")))
                    }
                })
            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }
    }

    suspend fun getRoute(origin: LatLng, destination: LatLng): Result<List<Route>> {
        return withContext(Dispatchers.IO) {
            try {
                val mapplsResult = getMapplsDirections(origin, destination)
                if (mapplsResult.isSuccess && !mapplsResult.getOrNull().isNullOrEmpty()) {
                    mapplsResult
                } else {
                    osrmRoutingService.getRoutes(origin, destination)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mappls Directions failed, falling back to OSRM", e)
                osrmRoutingService.getRoutes(origin, destination)
            }
        }
    }

    private suspend fun getMapplsDirections(origin: LatLng, destination: LatLng): Result<List<Route>> {
        return kotlin.coroutines.suspendCoroutine { continuation ->
            try {
                val originPoint = Point.fromLngLat(origin.longitude, origin.latitude)
                val destPoint = Point.fromLngLat(destination.longitude, destination.latitude)

                val directions = MapplsDirections.builder()
                    .origin(originPoint)
                    .destination(destPoint)
                    .profile(DirectionsCriteria.PROFILE_DRIVING)
                    .resource(DirectionsCriteria.RESOURCE_ROUTE_ETA)
                    .steps(true)
                    .overview(DirectionsCriteria.OVERVIEW_FULL)
                    .build()

                MapplsDirectionManager.newInstance(directions).call(object : OnResponseCallback<DirectionsResponse> {
                    override fun onSuccess(response: DirectionsResponse?) {
                        val routes = response?.routes() ?: emptyList()
                        val mappedRoutes = routes.mapIndexed { index, route ->
                            val coordinates = route.geometry()?.let { parsePolylineGeometry(it) } ?: emptyList()
                            val distance = route.distance() ?: 0.0
                            val duration = route.duration() ?: 0.0
                            Route(
                                id = "mappls_route_$index",
                                distanceMeters = distance,
                                durationSeconds = duration,
                                coordinates = coordinates,
                                comfortScore = (100.0 - index * 15.0).coerceAtLeast(20.0),
                                isRecommended = index == 0
                            )
                        }
                        if (mappedRoutes.isNotEmpty()) {
                            continuation.resume(Result.success(mappedRoutes))
                        } else {
                            continuation.resume(Result.failure(Exception("No routes in Mappls response")))
                        }
                    }

                    override fun onError(code: Int, message: String?) {
                        continuation.resume(Result.failure(Exception("Mappls directions error $code: $message")))
                    }
                })
            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }
    }

    private fun parsePolylineGeometry(polylineStr: String): List<LatLng> {
        return com.mappls.sdk.geojson.utils.PolylineUtils.decode(polylineStr, 6)
            .map { LatLng(it.latitude(), it.longitude()) }
    }

    companion object {
        private const val TAG = "MapplsSearchService"

        @Volatile
        private var INSTANCE: MapplsSearchService? = null

        fun getInstance(): MapplsSearchService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MapplsSearchService().also { INSTANCE = it }
            }
        }
    }
}
