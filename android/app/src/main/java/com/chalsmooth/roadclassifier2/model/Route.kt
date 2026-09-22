package com.chalsmooth.roadclassifier2.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Route(
    val id: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val coordinates: List<LatLng>,
    val potholeCount: Int = 0,
    val roughnessScore: Double = 0.0,
    val comfortScore: Double = 0.0,
    val isRecommended: Boolean = false
) : Parcelable {

    fun getDistanceKm(): Double = distanceMeters / 1000.0

    fun getDurationMinutes(): Double = durationSeconds / 60.0

    fun getFormattedDistance(): String {
        return if (distanceMeters >= 1000) {
            String.format("%.1f km", getDistanceKm())
        } else {
            String.format("%.0f m", distanceMeters)
        }
    }

    fun getFormattedDuration(): String {
        val minutes = getDurationMinutes()
        return if (minutes >= 60) {
            val hours = (minutes / 60).toInt()
            val mins = (minutes % 60).toInt()
            "${hours}h ${mins}m"
        } else {
            "${minutes.toInt()} min"
        }
    }

    fun getComfortColor(): Int {
        return when {
            comfortScore >= 80 -> 0xFF4CAF50.toInt() // Green
            comfortScore >= 60 -> 0xFF2196F3.toInt() // Blue
            comfortScore >= 40 -> 0xFFFF9800.toInt() // Yellow
            comfortScore >= 20 -> 0xFFFF9800.toInt() // Orange
            else -> 0xFFF44336.toInt() // Red
        }
    }

    fun getComfortLabel(): String {
        return when {
            comfortScore >= 80 -> "Recommended"
            comfortScore >= 60 -> "Good"
            comfortScore >= 40 -> "Moderate"
            comfortScore >= 20 -> "Rough"
            else -> "Avoid"
        }
    }
}