package com.personal.workhours

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.personal.workhours.ui.navigation.WorkHoursApp

class MainActivity : ComponentActivity() {

    private var openDashboardTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeDashboardExtra(intent)
        val container = (application as WorkHoursApplication).container
        setContent {
            WorkHoursApp(
                container = container,
                openDashboardTrigger = openDashboardTrigger
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDashboardExtra(intent)
    }

    private fun consumeDashboardExtra(sourceIntent: Intent?) {
        if (sourceIntent?.getBooleanExtra(EXTRA_OPEN_DASHBOARD, false) == true) {
            sourceIntent.removeExtra(EXTRA_OPEN_DASHBOARD)
            openDashboardTrigger++
        }
    }

    companion object {
        const val EXTRA_OPEN_DASHBOARD = "extra_open_dashboard"
    }
}
