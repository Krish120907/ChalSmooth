package com.chalsmooth.roadclassifier2.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class GeocodingResult(
    val placeId: String,
    val displayName: String,
    val coordinate: LatLng,
    val addressType: String,
    val importance: Double
) : Parcelable