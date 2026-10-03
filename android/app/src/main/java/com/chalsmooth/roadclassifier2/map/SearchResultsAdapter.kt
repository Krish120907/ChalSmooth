package com.chalsmooth.roadclassifier2.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chalsmooth.roadclassifier2.R
import com.chalsmooth.roadclassifier2.model.GeocodingResult
import com.chalsmooth.roadclassifier2.model.LatLng

sealed class SearchItem {
    data class Result(val geocodingResult: GeocodingResult) : SearchItem()
    data class History(val item: SearchHistoryManager.HistoryItem) : SearchItem()
    data class Header(val title: String) : SearchItem()
}

class SearchResultsAdapter(
    private val onItemClick: (SearchItem) -> Unit,
    private val onHistoryClick: (SearchHistoryManager.HistoryItem) -> Unit = {},
    private val onBookmarkClick: (GeocodingResult) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items = emptyList<SearchItem>()
    private var currentLocation: LatLng? = null

    companion object {
        private const val TYPE_RESULT = 0
        private const val TYPE_HISTORY = 1
        private const val TYPE_HEADER = 2
    }

    inner class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPlaceIcon: ImageView = view.findViewById(R.id.ivPlaceIcon)
        val tvPlaceName: TextView = view.findViewById(R.id.tvPlaceName)
        val tvPlaceAddress: TextView = view.findViewById(R.id.tvPlaceAddress)
        val tvPlaceDistance: TextView = view.findViewById(R.id.tvPlaceDistance)
        val ivBookmark: ImageView = view.findViewById(R.id.ivBookmark)
    }

    inner class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPlaceIcon: ImageView = view.findViewById(R.id.ivPlaceIcon)
        val tvPlaceName: TextView = view.findViewById(R.id.tvPlaceName)
        val tvPlaceAddress: TextView = view.findViewById(R.id.tvPlaceAddress)
        val tvPlaceDistance: TextView = view.findViewById(R.id.tvPlaceDistance)
        val ivHistoryIcon: ImageView = view.findViewById(R.id.ivHistoryIcon)
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(R.id.tvHeader)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SearchItem.Result -> TYPE_RESULT
            is SearchItem.History -> TYPE_HISTORY
            else -> TYPE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_RESULT -> ResultViewHolder(inflater.inflate(R.layout.item_search_result, parent, false))
            TYPE_HISTORY -> HistoryViewHolder(inflater.inflate(R.layout.item_search_history, parent, false))
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_search_header, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ResultViewHolder -> {
                val item = items[position] as SearchItem.Result
                holder.tvPlaceName.text = item.geocodingResult.displayName.split(",").first()
                holder.tvPlaceAddress.text = item.geocodingResult.displayName
                holder.tvPlaceDistance.text = calculateDistance(item.geocodingResult.coordinate)
                holder.itemView.setOnClickListener { onItemClick(item) }
                
                // Bookmark button
                val bookmarkManager = BookmarkManager.getInstance(holder.itemView.context)
                val isBookmarked = bookmarkManager.getAllBookmarks().any { 
                    it.latitude == item.geocodingResult.coordinate.latitude && 
                    it.longitude == item.geocodingResult.coordinate.longitude 
                }
                holder.ivBookmark.setImageResource(if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline)
                holder.ivBookmark.setOnClickListener { v ->
                    val newBookmarkState = !isBookmarked
                    if (newBookmarkState) {
                        onBookmarkClick(item.geocodingResult)
                    } else {
                        // Remove bookmark - find and delete
                        val bookmark = bookmarkManager.getAllBookmarks().firstOrNull { 
                            it.latitude == item.geocodingResult.coordinate.latitude && 
                            it.longitude == item.geocodingResult.coordinate.longitude 
                        }
                        bookmark?.let { bookmarkManager.deleteBookmark(it.id) }
                    }
                    holder.ivBookmark.setImageResource(if (newBookmarkState) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline)
                }
            }
            is HistoryViewHolder -> {
                val item = items[position] as SearchItem.History
                holder.tvPlaceName.text = item.item.displayName.split(",").first()
                holder.tvPlaceAddress.text = item.item.displayName
                holder.tvPlaceDistance.text = calculateDistance(item.item.coordinate)
                holder.ivPlaceIcon.setImageResource(R.drawable.ic_history)
                holder.itemView.setOnClickListener { onHistoryClick(item.item) }
            }
            is HeaderViewHolder -> {
                val item = items[position] as SearchItem.Header
                holder.tvHeader.text = item.title
            }
        }
    }

    private fun calculateDistance(coordinate: LatLng?): String {
        currentLocation?.let { loc ->
            val distance = haversineDistance(loc, coordinate!!)
            return if (distance < 1000) {
                "${distance.toInt()} m"
            } else {
                String.format("%.1f km", distance / 1000)
            }
        }
        return ""
    }

    private fun haversineDistance(loc1: LatLng, loc2: LatLng): Double {
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

    override fun getItemCount(): Int = items.size

    fun updateResults(newResults: List<GeocodingResult>, history: List<SearchHistoryManager.HistoryItem> = emptyList(), currentLoc: LatLng? = null) {
        currentLocation = currentLoc
        val list = mutableListOf<SearchItem>()
        if (newResults.isNotEmpty()) {
            list.add(SearchItem.Header("RESULTS"))
            list.addAll(newResults.map { SearchItem.Result(it) })
        }
        if (history.isNotEmpty() && newResults.isEmpty()) {
            list.add(SearchItem.Header("RECENT SEARCHES"))
            list.addAll(history.map { SearchItem.History(it) })
        } else if (history.isNotEmpty()) {
            list.add(SearchItem.Header("HISTORY"))
            list.addAll(history.map { SearchItem.History(it) })
        }
        
        items = list
        notifyDataSetChanged()
    }

    fun updateLocation(currentLoc: LatLng?) {
        currentLocation = currentLoc
        notifyDataSetChanged()
    }

    fun clear() {
        items = emptyList()
        notifyDataSetChanged()
    }
}