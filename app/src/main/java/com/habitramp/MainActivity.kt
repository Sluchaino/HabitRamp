package com.habitramp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.habitramp.core.notification.NotificationScheduler
import com.habitramp.presentation.navigation.HabitRampNavHost
import com.habitramp.presentation.theme.HabitRampTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HabitRampApp
        val habitId = intent
            .getLongExtra(NotificationScheduler.EXTRA_HABIT_ID, -1L)
            .takeIf { it > 0 }
        setContent {
            HabitRampTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HabitRampNavHost(app.container, habitId)
                }
            }
        }
    }
}
