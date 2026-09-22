package com.chalsmooth.roadclassifier2.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class LatLng(
    val latitude: Double,
    val longitude: Double
) : Parcelable {

    fun toMapplsLatLng(): com.mappls.sdk.maps.geometry.LatLng =
        com.mappls.sdk.maps.geometry.LatLng(latitude, longitude)

    companion object {
        fun fromMapplsLatLng(latLng: com.mappls.sdk.maps.geometry.LatLng): LatLng =
            LatLng(latLng.latitude, latLng.longitude)
    }
}