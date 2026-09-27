package com.chalsmooth.roadclassifier2.network

import android.content.Context
import android.util.Log
import com.chalsmooth.roadclassifier2.data.PotholeEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PotholeApiClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    fun getServerBaseUrl(): String {
        val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
        return prefs.getString("server_url", "http://10.0.2.2:5000") ?: "http://10.0.2.2:5000"
    }

    fun setServerBaseUrl(url: String) {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length - 1)
        }
        val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("server_url", cleanUrl).apply()
    }

    suspend fun postPothole(entry: PotholeEntry): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getServerBaseUrl()
            val url = "$baseUrl/api/potholes"

            val json = JSONObject().apply {
                put("id", entry.id)
                put("latitude", entry.latitude)
                put("longitude", entry.longitude)
                put("label", entry.label)
                put("confidence", entry.confidence)
                put("timestamp", entry.timestamp)
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("ngrok-skip-browser-warning", "true")
                .addHeader("User-Agent", "ChalSmoothAndroidApp")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful
            response.close()
            Log.d(TAG, "Post pothole to $url: success=$isSuccess")
            isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post pothole to backend server", e)
            false
        }
    }

    suspend fun fetchNearbyPotholes(lat: Double, lng: Double, radiusKm: Double = 10.0): List<PotholeEntry> = withContext(Dispatchers.IO) {
        val list = ArrayList<PotholeEntry>()
        try {
            val baseUrl = getServerBaseUrl()
            val url = "$baseUrl/api/potholes?lat=$lat&lng=$lng&radius_km=$radiusKm"

            val request = Request.Builder()
                .url(url)
                .addHeader("ngrok-skip-browser-warning", "true")
                .addHeader("User-Agent", "ChalSmoothAndroidApp")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string()
                if (!bodyString.isNullOrEmpty()) {
                    val jsonArray = JSONArray(bodyString)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val entry = PotholeEntry(
                            id = obj.optString("id"),
                            latitude = obj.getDouble("latitude"),
                            longitude = obj.getDouble("longitude"),
                            label = obj.optString("label", "Pothole"),
                            confidence = obj.optDouble("confidence", 1.0).toFloat(),
                            imagePath = "",
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                        list.add(entry)
                    }
                }
            }
            response.close()
            Log.d(TAG, "Fetched ${list.size} nearby potholes from server")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch nearby potholes from server", e)
        }
        list
    }

    companion object {
        private const val TAG = "PotholeApiClient"
    }
}
