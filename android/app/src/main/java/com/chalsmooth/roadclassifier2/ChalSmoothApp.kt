package com.chalsmooth.roadclassifier2

import android.app.Application
import android.util.Log
import com.mappls.sdk.maps.Mappls

class ChalSmoothApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            Mappls.getInstance(this)
            Log.d("ChalSmoothApp", "Mappls SDK initialized successfully")
        } catch (e: Exception) {
            Log.e("ChalSmoothApp", "Mappls SDK initialization failed: ${e.message}", e)
        }
    }
}
