package com.chalsmooth.roadclassifier2.map

import android.content.Context
import android.content.SharedPreferences
import com.chalsmooth.roadclassifier2.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BookmarkManager private constructor(private val prefs: SharedPreferences) {

    private val gson = Gson()
    private val BOOKMARKS_KEY = "bookmarks"
    private val COLLECTIONS_KEY = "collections"

    data class Bookmark(
        val id: String,
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val collectionId: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class Collection(
        val id: String,
        val name: String,
        val color: Long = 0xFF1976D2L,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val colorInt: Int get() = color.toInt()
    }

    fun getAllBookmarks(): List<Bookmark> {
        val json = prefs.getString(BOOKMARKS_KEY, "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<Bookmark>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getBookmarksByCollection(collectionId: String): List<Bookmark> {
        return getAllBookmarks().filter { it.collectionId == collectionId }
    }

    fun getCollections(): List<Collection> {
        val json = prefs.getString(COLLECTIONS_KEY, "[]") ?: "[]"
        val collections = try {
            gson.fromJson(json, object : TypeToken<List<Collection>>() {}.type)
        } catch (e: Exception) {
            emptyList<Collection>()
        }
        if (collections.isEmpty()) {
            val defaultCollection = Collection("default", "Favorites")
            saveCollections(listOf(defaultCollection))
            return listOf(defaultCollection)
        }
        return collections
    }

    fun createCollection(name: String, color: Long = 0xFF1976D2L): Collection {
        val collections = getCollections()
        val newCollection = Collection(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            color = color
        )
        saveCollections(collections + newCollection)
        return newCollection
    }

    fun saveBookmark(
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
        collectionId: String
    ): Bookmark? {
        val bookmarks = getAllBookmarks()
        
        // Check if already bookmarked in this collection (same coordinates)
        val existingInCollection = bookmarks.firstOrNull { b ->
            b.collectionId == collectionId &&
            b.latitude == latitude &&
            b.longitude == longitude
        }
        
        if (existingInCollection != null) {
            return null // Already bookmarked in this collection
        }
        
        val newBookmark = Bookmark(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            collectionId = collectionId
        )
        saveBookmarks(bookmarks + newBookmark)
        return newBookmark
    }

    fun deleteBookmark(bookmarkId: String) {
        val bookmarks = getAllBookmarks().filter { it.id != bookmarkId }
        saveBookmarks(bookmarks)
    }

    fun deleteCollection(collectionId: String) {
        if (collectionId == "default") return
        val collections = getCollections().filter { it.id != collectionId }
        saveCollections(collections)
        val bookmarks = getAllBookmarks().filter { it.collectionId != collectionId }
        saveBookmarks(bookmarks)
    }

    fun updateCollection(collection: Collection) {
        val collections = getCollections().map { if (it.id == collection.id) collection else it }
        saveCollections(collections)
    }

    private fun saveBookmarks(bookmarks: List<Bookmark>) {
        val json = gson.toJson(bookmarks)
        prefs.edit().putString(BOOKMARKS_KEY, json).apply()
    }

    private fun saveCollections(collections: List<Collection>) {
        val json = gson.toJson(collections)
        prefs.edit().putString(COLLECTIONS_KEY, json).apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: BookmarkManager? = null

        fun getInstance(context: Context): BookmarkManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BookmarkManager(
                    context.getSharedPreferences("bookmarks_prefs", Context.MODE_PRIVATE)
                ).also { INSTANCE = it }
            }
        }
    }
}