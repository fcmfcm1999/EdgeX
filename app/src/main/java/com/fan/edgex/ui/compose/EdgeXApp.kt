package com.fan.edgex.ui.compose

import android.content.Context
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
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
import com.fan.edgex.R
import com.fan.edgex.license.PremiumActivator
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.ModuleActivationState
import com.fan.edgex.config.configPrefs
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.ui.compose.components.EdgeXToast
import com.fan.edgex.ui.compose.screens.AboutScreen
import com.fan.edgex.ui.compose.screens.EdgeLightingScreen
import com.fan.edgex.ui.compose.screens.FluidEffectScreen
import com.fan.edgex.ui.compose.screens.FreezerScreen
import com.fan.edgex.ui.compose.screens.GesturesScreen
import com.fan.edgex.ui.compose.screens.HomeCallbacks
import com.fan.edgex.ui.compose.screens.HomeScreen
import com.fan.edgex.ui.compose.screens.HomeStats
import com.fan.edgex.ui.compose.screens.KeysScreen
import com.fan.edgex.ui.compose.screens.MultiScreen
import com.fan.edgex.ui.compose.screens.PieScreen
import com.fan.edgex.ui.compose.screens.CustomPanelScreen
import com.fan.edgex.ui.compose.screens.PremiumScreen
import com.fan.edgex.ui.compose.screens.SideBarScreen
import com.fan.edgex.ui.compose.screens.ThemeScreen
import com.fan.edgex.ui.compose.theme.EdgeXAccent
import com.fan.edgex.ui.compose.theme.EdgeXTheme
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import kotlinx.coroutines.delay

enum class EdgeXRoute(@StringRes val labelRes: Int) {
    Home(R.string.compose_route_home),
    Gestures(R.string.header_gestures),
    Keys(R.string.header_keys),
    Freezer(R.string.header_freezer),
    Pie(R.string.header_pie_settings),
    CustomPanel(R.string.menu_custom_panel),
    SideBar(R.string.menu_side_bar),
    Multi(R.string.menu_multi_actions),
    Theme(R.string.header_theme),
    EdgeLighting(R.string.menu_edge_lighting),
    FluidEffect(R.string.menu_fluid_effect),
    Premium(R.string.menu_premium),
    About(R.string.menu_about),
}

data class HomeUiState(
    val stats: HomeStats,
    val gesturesEnabled: Boolean,
    val debug: Boolean,
    val haptic: Boolean,
    val arcDrawer: Boolean,
    val keysEnabled: Boolean,
    val edgeLighting: Boolean,
    val moduleActive: Boolean,
    val accent: EdgeXAccent,
    val darkMode: Boolean,
    val premiumStatus: PremiumActivator.Status,
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

    fun popRoute() {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
    }

    fun popRouteAndRefresh() {
        refresh()
        popRoute()
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1800)
            toast = null
        }
    }

    LaunchedEffect(Unit) {
        ModuleActivationState.requestRefresh(context)
        delay(350)
        refresh()
    }

    EdgeXTheme(darkTheme = uiState.darkMode, accent = uiState.accent) {
        val colors = LocalEdgeXColors.current
        BackHandler(enabled = stack.size > 1) {
            when (stack.last()) {
                EdgeXRoute.Gestures,
                EdgeXRoute.Theme -> popRouteAndRefresh()
                else -> popRoute()
            }
        }
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
                    onBack = ::popRouteAndRefresh,
                    showToast = ::showToast,
                )
                EdgeXRoute.Freezer -> FreezerScreen(
                    onBack = ::popRouteAndRefresh,
                    showToast = ::showToast,
                )
                EdgeXRoute.Keys -> KeysScreen(
                    onBack = ::popRouteAndRefresh,
                    showToast = ::showToast,
                )
                EdgeXRoute.Pie -> PieScreen(
                    onBack = ::popRoute,
                )
                EdgeXRoute.CustomPanel -> CustomPanelScreen(
                    onBack = ::popRoute,
                )
                EdgeXRoute.SideBar -> SideBarScreen(
                    onBack = ::popRoute,
                )
                EdgeXRoute.Multi -> MultiScreen(
                    onBack = ::popRoute,
                    showToast = ::showToast,
                )
                EdgeXRoute.Theme -> ThemeScreen(
                    onBack = ::popRouteAndRefresh,
                    onThemeChanged = ::refresh,
                    showToast = ::showToast,
                )
                EdgeXRoute.EdgeLighting -> EdgeLightingScreen(
                    onBack = ::popRoute,
                    showToast = ::showToast,
                )
                EdgeXRoute.FluidEffect -> FluidEffectScreen(
                    onBack = ::popRoute,
                    showToast = ::showToast,
                )
                EdgeXRoute.Premium -> PremiumScreen(
                    onBack = ::popRoute,
                    onOpenEdgeLighting = { stack.add(EdgeXRoute.EdgeLighting) },
                    onOpenFluidEffect = { stack.add(EdgeXRoute.FluidEffect) },
                    showToast = ::showToast,
                )
                EdgeXRoute.About -> AboutScreen(
                    onBack = ::popRoute,
                    showToast = ::showToast,
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
        gesturesEnabled = getConfigBool(AppConfig.GESTURES_ENABLED),
        debug = getConfigBool(AppConfig.DEBUG_MATRIX),
        haptic = getConfigBool(AppConfig.HAPTIC_FEEDBACK, default = true),
        arcDrawer = getConfigBool(AppConfig.FREEZER_ARC_DRAWER),
        keysEnabled = getConfigBool(AppConfig.KEYS_ENABLED),
        edgeLighting = getConfigBool(AppConfig.EDGE_LIGHTING_ENABLED, default = true),
        moduleActive = ModuleActivationState.isActive(this),
        accent = EdgeXAccent.fromId(getConfigString(AppConfig.UI_ACCENT, EdgeXAccent.Default.id)),
        darkMode = getConfigBool(AppConfig.UI_DARK_MODE),
        premiumStatus = PremiumActivator.status(this),
    )

private fun Context.readHomeStats(): HomeStats {
    val configuredGestures = AppConfig.ZONES.sumOf { zone ->
        AppConfig.GESTURES.count { gesture ->
            val value = getConfigString(AppConfig.gestureAction(zone, gesture), "none")
            value.isNotBlank() && value != "none"
        }
    }
    val prefs = configPrefs()
    val activeZones = AppConfig.ZONES.count { zone ->
        val enabledKey = AppConfig.zoneEnabled(zone)
        if (prefs.contains(enabledKey)) {
            getConfigBool(enabledKey)
        } else {
            AppConfig.GESTURES.any { gesture ->
                AppConfig.isActiveActionValue(getConfigString(AppConfig.gestureAction(zone, gesture), "none"))
            }
        }
    }
    val frozenCount = prefs
        .getString(AppConfig.FREEZER_APP_LIST, null)
        ?.split(',')
        ?.count { it.isNotBlank() }
        ?: 0
    val keyCount = if (getConfigBool(AppConfig.KEYS_ENABLED)) {
        listOf(24, 25, 26).count { keyCode ->
            val enabledKey = AppConfig.keyEnabled(keyCode)
            if (prefs.contains(enabledKey)) {
                getConfigBool(enabledKey)
            } else {
                AppConfig.KEY_TRIGGERS.any { trigger ->
                    AppConfig.isActiveActionValue(getConfigString(AppConfig.keyAction(keyCode, trigger), "none"))
                }
            }
        }
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
