package com.chalsmooth.roadclassifier2.geocoding

import com.chalsmooth.roadclassifier2.model.GeocodingResult
import com.chalsmooth.roadclassifier2.model.LatLng
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
import retrofit2.http.Query

private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"
private const val USER_AGENT = "ChalSmooth/1.0 (krish@chalsmooth.com)"

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 10,
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("accept-language") acceptLanguage: String = "en"
    ): List<NominatimSearchResult>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("accept-language") acceptLanguage: String = "en"
    ): NominatimReverseResult
}

data class NominatimSearchResult(
    @Json(name = "place_id") val placeId: Long,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "lat") val lat: String,
    @Json(name = "lon") val lon: String,
    @Json(name = "type") val addressType: String,
    @Json(name = "importance") val importance: Double
) {
    fun toGeocodingResult(): GeocodingResult = GeocodingResult(
        placeId = placeId.toString(),
        displayName = displayName,
        coordinate = LatLng(lat.toDouble(), lon.toDouble()),
        addressType = addressType,
        importance = importance
    )
}

data class NominatimReverseResult(
    @Json(name = "place_id") val placeId: Long,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "lat") val lat: String,
    @Json(name = "lon") val lon: String,
    @Json(name = "type") val addressType: String
) {
    fun toGeocodingResult(): GeocodingResult = GeocodingResult(
        placeId = placeId.toString(),
        displayName = displayName,
        coordinate = LatLng(lat.toDouble(), lon.toDouble()),
        addressType = addressType,
        importance = 1.0
    )
}

class GeocodingService private constructor() {

    private val moshi = Moshi.Builder().build()
    private val retrofit: Retrofit
    private val api: NominatimApi

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(NOMINATIM_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        api = retrofit.create(NominatimApi::class.java)
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        private var INSTANCE: GeocodingService? = null

        fun getInstance(): GeocodingService {
            if (INSTANCE == null) {
                INSTANCE = GeocodingService()
            }
            return INSTANCE!!
        }
    }

    suspend fun search(query: String): Result<List<GeocodingResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val results = api.search(query)
                Result.success(results.map { it.toGeocodingResult() })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun reverseGeocode(coordinate: LatLng): Result<GeocodingResult> {
        return withContext(Dispatchers.IO) {
            try {
                val result = api.reverse(coordinate.latitude, coordinate.longitude)
                Result.success(result.toGeocodingResult())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}