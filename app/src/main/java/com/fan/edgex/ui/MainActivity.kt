package com.fan.edgex.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.config.FreezerBootstrap
import com.fan.edgex.config.broadcastFullConfigSnapshot
import com.fan.edgex.ui.compose.EdgeXApp
import com.fan.edgex.utils.UpdateChecker

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        broadcastFullConfigSnapshot()
        FreezerBootstrap.ensureMigrated(this)
        UpdateChecker.checkOnLaunch(this)
        setContent {
            EdgeXApp()
        }
    }
}
