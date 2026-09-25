package com.chalsmooth.roadclassifier2.map

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.chalsmooth.roadclassifier2.R

class ContributeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contribute)
        
        findViewById<LinearLayout>(R.id.btnNavHome).setOnClickListener {
            // Just finish this activity to return to home (MapScreen)
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
