package com.chalsmooth.roadclassifier2.map

import android.content.Context
import android.content.SharedPreferences
import com.chalsmooth.roadclassifier2.model.GeocodingResult
import com.chalsmooth.roadclassifier2.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SearchHistoryManager private constructor(private val prefs: SharedPreferences) {

    private val gson = Gson()
    private val HISTORY_KEY = "search_history"
    private val MAX_HISTORY_SIZE = 20

    data class HistoryItem(
        val query: String,
        val displayName: String,
        val coordinate: LatLng?,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun saveSearch(result: GeocodingResult) {
        val history = getHistory()
        val newItem = HistoryItem(
            query = result.displayName,
            displayName = result.displayName,
            coordinate = result.coordinate
        )
        
        val filtered = history.filter { it.query != newItem.query }
        val updated = (listOf(newItem) + filtered).take(MAX_HISTORY_SIZE)
        saveHistory(updated)
    }

    fun saveSearch(query: String, displayName: String, coordinate: LatLng?) {
        val history = getHistory()
        val newItem = HistoryItem(query, displayName, coordinate)
        val filtered = history.filter { it.query != newItem.query }
        val updated = (listOf(newItem) + filtered).take(MAX_HISTORY_SIZE)
        saveHistory(updated)
    }

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString(HISTORY_KEY, "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<HistoryItem>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearHistory() {
        prefs.edit().remove(HISTORY_KEY).apply()
    }

    private fun saveHistory(history: List<HistoryItem>) {
        val json = gson.toJson(history)
        prefs.edit().putString(HISTORY_KEY, json).apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: SearchHistoryManager? = null

        fun getInstance(context: Context): SearchHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SearchHistoryManager(
                    context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
                ).also { INSTANCE = it }
            }
        }
    }
}