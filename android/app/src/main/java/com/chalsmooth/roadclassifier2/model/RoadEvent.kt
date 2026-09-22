package com.chalsmooth.roadclassifier2.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class RoadEvent(
    val id: String,
    val coordinate: LatLng,
    val type: RoadEventType,
    val severity: Float,
    val confidence: Float,
    val timestamp: Long
) : Parcelable

enum class RoadEventType {
    POTHOLE,
    ROUGH_ROAD,
    SPEED_BUMP,
    HARD_BRAKING,
    NORMAL
}