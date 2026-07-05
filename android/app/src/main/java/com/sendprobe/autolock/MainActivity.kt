package com.sendprobe.autolock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sendprobe.autolock.ui.LogScreen
import com.sendprobe.autolock.ui.SettingsScreen
import com.sendprobe.autolock.ui.theme.AutolockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutolockTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "settings") {
                    composable("settings") {
                        SettingsScreen(onViewLogs = { navController.navigate("logs") })
                    }
                    composable("logs") {
                        LogScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
