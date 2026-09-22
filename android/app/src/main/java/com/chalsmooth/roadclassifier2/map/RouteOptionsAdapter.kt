package com.chalsmooth.roadclassifier2.map

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView
import com.chalsmooth.roadclassifier2.R
import com.chalsmooth.roadclassifier2.model.Route

class RouteOptionsAdapter(
    private val onRouteSelect: (Route) -> Unit,
    private val onRouteLongClick: (Route) -> Unit = {}
) : RecyclerView.Adapter<RouteOptionsAdapter.ViewHolder>() {

    private var routes = emptyList<Route>()
    private var selectedRouteId: String? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val vRouteColor: View = view.findViewById(R.id.vRouteColor)
        val tvRouteLabel: TextView = view.findViewById(R.id.tvRouteLabel)
        val tvComfortLabel: TextView = view.findViewById(R.id.tvComfortLabel)
        val tvRouteDistance: TextView = view.findViewById(R.id.tvRouteDistance)
        val tvRouteDuration: TextView = view.findViewById(R.id.tvRouteDuration)
        val tvRouteDescription: TextView = view.findViewById(R.id.tvRouteDescription)
        val ivSelected: ImageView = view.findViewById(R.id.ivSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val route = routes[position]
        val isSelected = route.id == selectedRouteId

        // Set color indicator
        holder.vRouteColor.setBackgroundColor(route.getComfortColor())
        holder.vRouteColor.visibility = View.VISIBLE

        // Route label (e.g., "Route 1", "Recommended")
        holder.tvRouteLabel.text = if (route.isRecommended) "Recommended" else "Route ${position + 1}"

        // Comfort label
        holder.tvComfortLabel.text = route.getComfortLabel()
        holder.tvComfortLabel.setTextColor(route.getComfortColor())
        holder.tvComfortLabel.visibility = View.VISIBLE

        // Distance and duration
        holder.tvRouteDistance.text = route.getFormattedDistance()
        holder.tvRouteDuration.text = route.getFormattedDuration()

        // Description
        val potholeText = if (route.potholeCount > 0) "${route.potholeCount} potholes" else "No potholes"
        holder.tvRouteDescription.text = "$potholeText • ${route.getComfortLabel()}"

        // Selected indicator
        holder.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

        // Click listeners
        holder.itemView.setOnClickListener { onRouteSelect(route) }
        holder.itemView.setOnLongClickListener { onRouteLongClick(route); true }
    }

    override fun getItemCount(): Int = routes.size

    fun updateRoutes(newRoutes: List<Route>) {
        routes = newRoutes
        // Auto-select recommended route if available
        val recommended = routes.firstOrNull { it.isRecommended }
        selectedRouteId = recommended?.id ?: routes.firstOrNull()?.id
        notifyDataSetChanged()
    }

    fun setSelectedRoute(routeId: String?) {
        selectedRouteId = routeId
        notifyDataSetChanged()
    }

    fun getSelectedRoute(): Route? = routes.firstOrNull { it.id == selectedRouteId }
}