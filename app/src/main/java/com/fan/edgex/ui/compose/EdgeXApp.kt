package com.fan.edgex.ui.compose

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.configPrefs
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.ui.EdgeLightingSettingsActivity
import com.fan.edgex.ui.FreezerActivity
import com.fan.edgex.ui.GesturesActivity
import com.fan.edgex.ui.KeysActivity
import com.fan.edgex.ui.MultiActionsListActivity
import com.fan.edgex.ui.PieSettingsActivity
import com.fan.edgex.ui.PremiumActivity
import com.fan.edgex.ui.ThemeActivity
import com.fan.edgex.ui.compose.components.EdgeXToast
import com.fan.edgex.ui.compose.screens.EdgeXPlaceholderScreen
import com.fan.edgex.ui.compose.screens.EdgeLightingScreen
import com.fan.edgex.ui.compose.screens.FreezerScreen
import com.fan.edgex.ui.compose.screens.GesturesScreen
import com.fan.edgex.ui.compose.screens.HomeCallbacks
import com.fan.edgex.ui.compose.screens.HomeScreen
import com.fan.edgex.ui.compose.screens.HomeStats
import com.fan.edgex.ui.compose.screens.KeysScreen
import com.fan.edgex.ui.compose.screens.MultiScreen
import com.fan.edgex.ui.compose.screens.PieScreen
import com.fan.edgex.ui.compose.screens.ThemeScreen
import com.fan.edgex.ui.compose.theme.EdgeXAccent
import com.fan.edgex.ui.compose.theme.EdgeXTheme
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import kotlinx.coroutines.delay

enum class EdgeXRoute(val label: String) {
    Home("主页"),
    Gestures("手势"),
    Freezer("冰箱"),
    Keys("按键"),
    Pie("Pie 菜单"),
    Multi("组合动作"),
    Theme("主题"),
    EdgeLighting("Edge Lighting"),
    Premium("Premium"),
    About("关于"),
}

data class HomeUiState(
    val stats: HomeStats,
    val debug: Boolean,
    val haptic: Boolean,
    val arcDrawer: Boolean,
    val keysEnabled: Boolean,
    val edgeLighting: Boolean,
    val accent: EdgeXAccent,
    val darkMode: Boolean,
)

@Composable
fun EdgeXApp() {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf(EdgeXRoute.Home) }
    var uiState by remember { mutableStateOf(context.readHomeUiState()) }
    var toast by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        uiState = context.readHomeUiState()
    }

    fun showToast(message: String) {
        toast = message
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1800)
            toast = null
        }
    }

    EdgeXTheme(darkTheme = uiState.darkMode, accent = uiState.accent) {
        val colors = LocalEdgeXColors.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            when (val route = stack.last()) {
                EdgeXRoute.Home -> HomeScreen(
                    state = uiState,
                    callbacks = HomeCallbacks(
                        openRoute = { stack.add(it) },
                        showToast = ::showToast,
                        setDebug = {
                            context.putConfig(AppConfig.DEBUG_MATRIX, it)
                            refresh()
                        },
                        setHaptic = {
                            context.putConfig(AppConfig.HAPTIC_FEEDBACK, it)
                            refresh()
                        },
                        setArcDrawer = {
                            context.putConfig(AppConfig.FREEZER_ARC_DRAWER, it)
                            refresh()
                        },
                    ),
                )
                EdgeXRoute.Gestures -> GesturesScreen(
                    onBack = {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    },
                    showToast = ::showToast,
                )
                EdgeXRoute.Freezer -> FreezerScreen(
                    onBack = {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    },
                    showToast = ::showToast,
                )
                EdgeXRoute.Keys -> KeysScreen(
                    onBack = {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    },
                    showToast = ::showToast,
                )
                EdgeXRoute.Pie -> PieScreen(
                    onBack = {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    },
                )
                EdgeXRoute.Multi -> MultiScreen(
                    onBack = {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    },
                    showToast = ::showToast,
                )
                EdgeXRoute.Theme -> ThemeScreen(
                    onBack = {
                        refresh()
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    },
                    onThemeChanged = ::refresh,
                    showToast = ::showToast,
                )
                EdgeXRoute.EdgeLighting -> EdgeLightingScreen(
                    onBack = {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    },
                    showToast = ::showToast,
                )
                else -> EdgeXPlaceholderScreen(
                    route = route,
                    onBack = {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    },
                    onOpenLegacy = {
                        context.openLegacyRoute(route)
                    },
                )
            }

            EdgeXToast(
                message = toast,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            )
        }
    }
}

private fun Context.readHomeUiState(): HomeUiState =
    HomeUiState(
        stats = readHomeStats(),
        debug = getConfigBool(AppConfig.DEBUG_MATRIX),
        haptic = getConfigBool(AppConfig.HAPTIC_FEEDBACK, default = true),
        arcDrawer = getConfigBool(AppConfig.FREEZER_ARC_DRAWER),
        keysEnabled = getConfigBool(AppConfig.KEYS_ENABLED),
        edgeLighting = getConfigBool(AppConfig.EDGE_LIGHTING_ENABLED, default = true),
        accent = EdgeXAccent.fromId(getConfigString(AppConfig.UI_ACCENT, EdgeXAccent.Green.id)),
        darkMode = getConfigBool(AppConfig.UI_DARK_MODE),
    )

private fun Context.readHomeStats(): HomeStats {
    val configuredGestures = AppConfig.ZONES.sumOf { zone ->
        AppConfig.GESTURES.count { gesture ->
            val value = getConfigString(AppConfig.gestureAction(zone, gesture), "none")
            value.isNotBlank() && value != "none"
        }
    }
    val activeZones = AppConfig.ZONES.count { zone ->
        AppConfig.GESTURES.any { gesture ->
            val value = getConfigString(AppConfig.gestureAction(zone, gesture), "none")
            value.isNotBlank() && value != "none"
        }
    }
    val frozenCount = configPrefs()
        .getString(AppConfig.FREEZER_APP_LIST, null)
        ?.split(',')
        ?.count { it.isNotBlank() }
        ?: 0
    val keyCount = if (getConfigBool(AppConfig.KEYS_ENABLED)) {
        AppConfig.KEY_TRIGGERS.count { trigger ->
            listOf(24, 25, 26).any { keyCode ->
                val value = getConfigString(AppConfig.keyAction(keyCode, trigger), "none")
                value.isNotBlank() && value != "none"
            }
        }.coerceAtLeast(3)
    } else {
        0
    }
    return HomeStats(
        configuredGestures = configuredGestures,
        activeZones = activeZones,
        frozenApps = frozenCount,
        keyCount = keyCount,
    )
}

private fun Context.openLegacyRoute(route: EdgeXRoute) {
    val target = when (route) {
        EdgeXRoute.Gestures -> GesturesActivity::class.java
        EdgeXRoute.Freezer -> FreezerActivity::class.java
        EdgeXRoute.Keys -> KeysActivity::class.java
        EdgeXRoute.Pie -> PieSettingsActivity::class.java
        EdgeXRoute.Multi -> MultiActionsListActivity::class.java
        EdgeXRoute.Theme -> ThemeActivity::class.java
        EdgeXRoute.EdgeLighting -> EdgeLightingSettingsActivity::class.java
        EdgeXRoute.Premium -> PremiumActivity::class.java
        EdgeXRoute.Home,
        EdgeXRoute.About -> null
    }
    if (target != null) {
        startActivity(Intent(this, target))
    } else if (this is Activity) {
        android.widget.Toast.makeText(this, route.label, android.widget.Toast.LENGTH_SHORT).show()
    }
}
