package com.fan.edgex.ui.compose.screens

import com.fan.edgex.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.R
import com.fan.edgex.ui.compose.EdgeXRoute
import com.fan.edgex.ui.compose.HomeUiState
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconBox
import com.fan.edgex.ui.compose.components.EdgeXIconButton
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTile
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

data class HomeStats(
    val configuredGestures: Int,
    val activeZones: Int,
    val frozenApps: Int,
    val keyCount: Int,
)

data class HomeCallbacks(
    val openRoute: (EdgeXRoute) -> Unit,
    val showToast: (String) -> Unit,
    val setDebug: (Boolean) -> Unit,
    val setHaptic: (Boolean) -> Unit,
    val setArcDrawer: (Boolean) -> Unit,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val searchPendingToast = stringResource(R.string.compose_search_pending_toast)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(
            title = "",
            trailing = {
                EdgeXIconButton(onClick = { callbacks.showToast(searchPendingToast) }) {
                    EdgeXIcon(EdgeXIcons.Search, contentDescription = stringResource(R.string.compose_search), tint = LocalEdgeXColors.current.onSurface)
                }
                EdgeXIconButton(onClick = { callbacks.openRoute(EdgeXRoute.About) }) {
                    EdgeXIcon(EdgeXIcons.More, contentDescription = stringResource(R.string.compose_more), tint = LocalEdgeXColors.current.onSurface)
                }
            },
        )
        AppHeader()
        HeroCard(state.stats, state.moduleActive)
        HomeTiles(state, callbacks)
        SectionLabel(stringResource(R.string.menu_advanced))
        AdvancedSettings(state, callbacks)
        SectionLabel(stringResource(R.string.compose_section_about))
        AboutSettings(callbacks)
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun AppHeader() {
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 38.sp,
            lineHeight = 40.sp,
        )
        Text(
            text = stringResource(R.string.compose_app_subtitle),
            color = colors.onSurfaceDim,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun HeroCard(stats: HomeStats, moduleActive: Boolean) {
    val colors = LocalEdgeXColors.current
    val statusColor = if (moduleActive) Color(0xFF4CAF50) else Color(0xFFFFB74D)
    val statusTextColor = if (moduleActive) colors.accentSoft else Color(0xFFB45309)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(EdgeXRadius.xl))
            .background(Brush.linearGradient(listOf(colors.accentSoft2, colors.accentSoft)))
            .padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.onAccentSoft)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor),
            )
            Text(
                text = stringResource(if (moduleActive) R.string.compose_module_active else R.string.compose_module_inactive),
                color = statusTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        Text(
            text = stringResource(R.string.compose_home_hero_title),
            color = colors.onAccentSoft,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 31.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.compose_home_hero_subtitle, stats.configuredGestures, stats.activeZones),
            color = colors.onAccentSoft.copy(alpha = 0.72f),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.onAccentSoft)
                .padding(6.dp),
        ) {
            HeroStat("${stats.configuredGestures}", stringResource(R.string.compose_stat_gestures), Modifier.weight(1f))
            HeroStat("${stats.keyCount}", stringResource(R.string.compose_stat_keys), Modifier.weight(1f))
            HeroStat("${stats.frozenApps}", stringResource(R.string.compose_stat_frozen), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = LocalEdgeXColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = colors.accentSoft2, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(label, color = colors.accentSoft2.copy(alpha = 0.70f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

@Composable
private fun HomeTiles(state: HomeUiState, callbacks: HomeCallbacks) {
    val colors = LocalEdgeXColors.current
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TileRow {
            FeatureTile(
                title = stringResource(R.string.menu_gestures),
                meta = if (state.gesturesEnabled) stringResource(R.string.compose_home_gesture_meta, state.stats.activeZones) else stringResource(R.string.compose_disabled),
                icon = EdgeXIcons.Gesture,
                onClick = { callbacks.openRoute(EdgeXRoute.Gestures) },
                tag = "home_tile_gestures",
                modifier = Modifier.weight(1f),
            )
            FeatureTile(
                title = stringResource(R.string.menu_keys),
                meta = if (state.keysEnabled) stringResource(R.string.compose_enabled) else stringResource(R.string.compose_disabled),
                icon = EdgeXIcons.Keys,
                onClick = { callbacks.openRoute(EdgeXRoute.Keys) },
                tag = "home_tile_keys",
                modifier = Modifier.weight(1f),
            )
        }
        TileRow {
            FeatureTile(
                title = stringResource(R.string.menu_freezer),
                meta = stringResource(R.string.compose_home_freezer_meta, state.stats.frozenApps),
                icon = EdgeXIcons.Freeze,
                onClick = { callbacks.openRoute(EdgeXRoute.Freezer) },
                tag = "home_tile_freezer",
                modifier = Modifier.weight(1f),
            )
            FeatureTile(
                title = stringResource(R.string.header_pie_settings),
                meta = stringResource(R.string.compose_home_pie_meta),
                icon = EdgeXIcons.Pie,
                onClick = { callbacks.openRoute(EdgeXRoute.Pie) },
                tag = "home_tile_pie",
                modifier = Modifier.weight(1f),
            )
        }
        TileRow {
            FeatureTile(
                title = stringResource(R.string.menu_custom_panel),
                meta = stringResource(R.string.compose_home_custom_panel_meta),
                icon = EdgeXIcons.CustomPanel,
                onClick = { callbacks.openRoute(EdgeXRoute.CustomPanel) },
                tag = "home_tile_custom_panel",
                modifier = Modifier.weight(1f),
            )
            FeatureTile(
                title = stringResource(R.string.menu_side_bar),
                meta = stringResource(R.string.compose_home_side_bar_meta),
                icon = EdgeXIcons.SideBar,
                onClick = { callbacks.openRoute(EdgeXRoute.SideBar) },
                tag = "home_tile_side_bar",
                modifier = Modifier.weight(1f),
            )
        }
        TileRow {
            FeatureTile(
                title = stringResource(R.string.action_multi_action),
                meta = stringResource(R.string.compose_home_multi_meta),
                icon = EdgeXIcons.Multi,
                onClick = { callbacks.openRoute(EdgeXRoute.Multi) },
                tag = "home_tile_multi",
                modifier = Modifier.weight(1f),
            )
            FeatureTile(
                title = stringResource(R.string.header_theme),
                meta = stringResource(R.string.compose_home_theme_meta),
                icon = EdgeXIcons.Theme,
                onClick = { callbacks.openRoute(EdgeXRoute.Theme) },
                tag = "home_tile_theme",
                modifier = Modifier.weight(1f),
            )
        }
        EdgeXTile(
            title = stringResource(R.string.compose_premium_title),
            meta = stringResource(R.string.compose_premium_unlock),
            icon = EdgeXIcons.Sparkle,
            onClick = { callbacks.openRoute(EdgeXRoute.Premium) },
            modifier = Modifier
                .testTag("home_tile_premium")
                .fillMaxWidth(),
            trailing = {
                EdgeXIcon(
                    imageVector = EdgeXIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.onSurfaceDim,
                )
            },
        )
    }
}

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun FeatureTile(
    title: String,
    meta: String,
    icon: Int,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    iconBackground: Color = LocalEdgeXColors.current.accentSoft,
    iconTint: Color = LocalEdgeXColors.current.onAccentSoft,
) {
    EdgeXTile(
        title = title,
        meta = meta,
        icon = icon,
        onClick = onClick,
        modifier = modifier
            .testTag(tag)
            .height(132.dp),
        iconBackground = iconBackground,
        iconTint = iconTint,
    )
}

@Composable
private fun SectionLabel(label: String) {
    val colors = LocalEdgeXColors.current
    Text(
        text = label,
        color = colors.onSurfaceDim,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 10.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AdvancedSettings(state: HomeUiState, callbacks: HomeCallbacks) {
    val restartToast = stringResource(R.string.compose_restart_sysui_toast)
    EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
        EdgeXSwitchRow(
            title = stringResource(R.string.menu_debug_matrix),
            subtitle = stringResource(R.string.menu_debug_matrix_desc),
            checked = state.debug,
            onCheckedChange = callbacks.setDebug,
            icon = EdgeXIcons.DeveloperMode,
        )
        EdgeXDivider()
        EdgeXSwitchRow(
            title = stringResource(R.string.menu_haptic_feedback),
            subtitle = if (state.haptic) stringResource(R.string.compose_haptic_enabled_light) else stringResource(R.string.menu_haptic_feedback_desc),
            checked = state.haptic,
            onCheckedChange = callbacks.setHaptic,
            icon = EdgeXIcons.Vibration,
        )
        EdgeXDivider()
        EdgeXSwitchRow(
            title = stringResource(R.string.menu_arc_drawer),
            subtitle = stringResource(R.string.compose_arc_drawer_desc),
            checked = state.arcDrawer,
            onCheckedChange = callbacks.setArcDrawer,
            icon = EdgeXIcons.ArcDrawer,
        )
        EdgeXDivider()
        EdgeXRow(
            title = stringResource(R.string.menu_restart_sysui),
            subtitle = stringResource(R.string.compose_restart_sysui_desc),
            icon = EdgeXIcons.Restart,
            onClick = { callbacks.showToast(restartToast) },
        ) {
            EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim)
        }
    }
}

@Composable
private fun AboutSettings(callbacks: HomeCallbacks) {
    EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
        EdgeXRow(
            title = stringResource(R.string.compose_about_support_author),
            subtitle = stringResource(R.string.compose_about_support_author_subtitle),
            icon = EdgeXIcons.Sparkle,
            onClick = { callbacks.openRoute(EdgeXRoute.Premium) },
        ) {
            EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim)
        }
        EdgeXDivider()
        EdgeXRow(
            title = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
            subtitle = stringResource(R.string.value_project_url),
            icon = EdgeXIcons.About,
            onClick = { callbacks.openRoute(EdgeXRoute.About) },
        ) {
            EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim)
        }
    }
}
