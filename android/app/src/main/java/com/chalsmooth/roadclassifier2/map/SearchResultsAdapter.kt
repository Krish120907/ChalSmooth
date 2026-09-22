package com.chalsmooth.roadclassifier2.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chalsmooth.roadclassifier2.R
import com.chalsmooth.roadclassifier2.model.GeocodingResult

class SearchResultsAdapter(
    private val onItemClick: (GeocodingResult) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

    private var results = emptyList<GeocodingResult>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPlaceIcon: ImageView = view.findViewById(R.id.ivPlaceIcon)
        val tvPlaceName: TextView = view.findViewById(R.id.tvPlaceName)
        val tvPlaceAddress: TextView = view.findViewById(R.id.tvPlaceAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]
        holder.tvPlaceName.text = result.displayName.split(",").first()
        holder.tvPlaceAddress.text = result.displayName
        
        holder.itemView.setOnClickListener { onItemClick(result) }
    }

    override fun getItemCount(): Int = results.size

    fun updateResults(newResults: List<GeocodingResult>) {
        results = newResults
        notifyDataSetChanged()
    }

    fun clear() {
        results = emptyList()
        notifyDataSetChanged()
    }
}