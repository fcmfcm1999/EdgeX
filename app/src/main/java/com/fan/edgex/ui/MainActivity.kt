package com.fan.edgex.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.fan.edgex.config.FreezerBootstrap
import com.fan.edgex.config.ModuleActivationState
import com.fan.edgex.config.broadcastFullConfigSnapshot
import com.fan.edgex.config.syncRuntimeEnableFlagsFromConfiguredActions
import com.fan.edgex.ui.compose.EdgeXApp

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        syncRuntimeEnableFlagsFromConfiguredActions()
        ModuleActivationState.requestRefresh(this)
        broadcastFullConfigSnapshot()
        FreezerBootstrap.ensureMigrated(this)
        setContent {
            EdgeXApp()
        }
    }
}
