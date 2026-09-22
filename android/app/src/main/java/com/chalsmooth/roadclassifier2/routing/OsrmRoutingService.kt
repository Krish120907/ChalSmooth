package com.chalsmooth.roadclassifier2.routing

import com.chalsmooth.roadclassifier2.model.LatLng
import com.chalsmooth.roadclassifier2.model.Route
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

private const val OSRM_BASE_URL = "https://router.project-osrm.org/"

interface OsrmApi {
    @GET("route/v1/driving/{coordinates}")
    suspend fun getRoute(
        @Path("coordinates") coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("steps") steps: Boolean = true,
        @Query("alternatives") alternatives: Boolean = true,
        @Query("annotations") annotations: String = "nodes"
    ): OsrmResponse
}

data class OsrmResponse(
    val code: String,
    val routes: List<OsrmRoute>?,
    val waypoints: List<OsrmWaypoint>?
) {
    fun isSuccessful(): Boolean = code == "Ok"
}

data class OsrmRoute(
    val distance: Double,
    val duration: Double,
    val geometry: OsrmGeometry,
    val legs: List<OsrmLeg>,
    val weight: Double,
    val weight_name: String
) {
    fun toRoute(id: String): Route {
        val coordinates = geometry.coordinates.map { LatLng(it[1], it[0]) }
        return Route(
            id = id,
            distanceMeters = distance,
            durationSeconds = duration,
            coordinates = coordinates
        )
    }
}

data class OsrmGeometry(
    @Json(name = "coordinates") val coordinates: List<List<Double>>,
    val type: String
)

data class OsrmLeg(
    val distance: Double,
    val duration: Double,
    val summary: String,
    val steps: List<OsrmStep>,
    val annotation: OsrmAnnotation?
)

data class OsrmStep(
    val distance: Double,
    val duration: Double,
    val geometry: OsrmGeometry,
    val name: String,
    val maneuver: OsrmManeuver
)

data class OsrmManeuver(
    val type: String,
    val modifier: String?,
    val location: List<Double>,
    val instruction: String
)

data class OsrmAnnotation(
    val nodes: List<Long>?
)

data class OsrmWaypoint(
    val location: List<Double>,
    val name: String,
    val hint: String
)

class OsrmRoutingService private constructor() {

    private val moshi = Moshi.Builder().build()
    private val retrofit: Retrofit
    private val api: OsrmApi

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(OSRM_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        api = retrofit.create(OsrmApi::class.java)
    }

    companion object {
        private var INSTANCE: OsrmRoutingService? = null

        fun getInstance(): OsrmRoutingService {
            if (INSTANCE == null) {
                INSTANCE = OsrmRoutingService()
            }
            return INSTANCE!!
        }
    }

    suspend fun getRoutes(origin: LatLng, destination: LatLng): Result<List<Route>> {
        return withContext(Dispatchers.IO) {
            try {
                val coordinates = "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}"
                val response = api.getRoute(coordinates)
                
                if (!response.isSuccessful() || response.routes == null || response.routes!!.isEmpty()) {
                    return@withContext Result.failure(Exception("No routes found: ${response.code}"))
                }

                val routes = response.routes!!.mapIndexed { index, osrmRoute ->
                    osrmRoute.toRoute("route_$index")
                }
                Result.success(routes)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}