package com.fan.edgex.ui.compose.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(
            title = "",
            trailing = {
                EdgeXIconButton(onClick = { callbacks.showToast("搜索即将迁移") }) {
                    EdgeXIcon(EdgeXIcons.Search, contentDescription = "搜索", tint = LocalEdgeXColors.current.onSurface)
                }
                EdgeXIconButton(onClick = { callbacks.openRoute(EdgeXRoute.About) }) {
                    EdgeXIcon(EdgeXIcons.More, contentDescription = "更多", tint = LocalEdgeXColors.current.onSurface)
                }
            },
        )
        AppHeader()
        HeroCard(state.stats)
        HomeTiles(state, callbacks)
        SectionLabel("高级设置")
        AdvancedSettings(state, callbacks)
        SectionLabel("关于")
        AboutSettings(callbacks)
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun AppHeader() {
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp)) {
        Text(
            text = "EdgeX",
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 38.sp,
            lineHeight = 40.sp,
        )
        Text(
            text = "边缘手势 · 智能交互",
            color = colors.onSurfaceDim,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun HeroCard(stats: HomeStats) {
    val colors = LocalEdgeXColors.current
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
                    .background(colors.accent),
            )
            Text("模块已激活", color = colors.accentSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(
            text = "你的手势\n掌控一切",
            color = colors.onAccentSoft,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 31.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = "已配置 ${stats.configuredGestures} 个手势动作 · ${stats.activeZones}/16 区域已启用",
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
            HeroStat("${stats.configuredGestures}", "手势", Modifier.weight(1f))
            HeroStat("${stats.frozenApps}", "已冻结", Modifier.weight(1f))
            HeroStat("${stats.keyCount}", "按键", Modifier.weight(1f))
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
                title = "手势",
                meta = "${state.stats.activeZones} 区域已配置",
                icon = EdgeXIcons.Gesture,
                onClick = { callbacks.openRoute(EdgeXRoute.Gestures) },
                tag = "home_tile_gestures",
                modifier = Modifier.weight(1f),
            )
            FeatureTile(
                title = "冰箱",
                meta = "${state.stats.frozenApps} 个已冻结",
                icon = EdgeXIcons.Freeze,
                onClick = { callbacks.openRoute(EdgeXRoute.Freezer) },
                tag = "home_tile_freezer",
                iconBackground = Color(0xFFD5E0FB),
                iconTint = Color(0xFF3B6CE5),
                modifier = Modifier.weight(1f),
            )
        }
        TileRow {
            FeatureTile(
                title = "按键",
                meta = if (state.keysEnabled) "已启用" else "未启用",
                icon = EdgeXIcons.Keys,
                onClick = { callbacks.openRoute(EdgeXRoute.Keys) },
                tag = "home_tile_keys",
                iconBackground = Color(0xFFF6E2B4),
                iconTint = Color(0xFFC68A1A),
                modifier = Modifier.weight(1f),
            )
            FeatureTile(
                title = "Pie 菜单",
                meta = "4 边缘 · 2 环",
                icon = EdgeXIcons.Pie,
                onClick = { callbacks.openRoute(EdgeXRoute.Pie) },
                tag = "home_tile_pie",
                modifier = Modifier.weight(1f),
            )
        }
        TileRow {
            FeatureTile(
                title = "组合动作",
                meta = "可复用动作序列",
                icon = EdgeXIcons.Multi,
                onClick = { callbacks.openRoute(EdgeXRoute.Multi) },
                tag = "home_tile_multi",
                iconBackground = colors.warnSoft,
                iconTint = colors.warn,
                modifier = Modifier.weight(1f),
            )
            FeatureTile(
                title = "主题",
                meta = "轻点切换风格",
                icon = EdgeXIcons.Theme,
                onClick = { callbacks.openRoute(EdgeXRoute.Theme) },
                tag = "home_tile_theme",
                modifier = Modifier.weight(1f),
            )
        }
        EdgeXTile(
            title = "Premium",
            meta = "解锁全部高级功能",
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
    EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
        EdgeXSwitchRow(
            title = "调试模式",
            subtitle = "显示手势触发区域",
            checked = state.debug,
            onCheckedChange = callbacks.setDebug,
            icon = EdgeXIcons.Theme,
        )
        EdgeXDivider()
        EdgeXSwitchRow(
            title = "震动反馈",
            subtitle = if (state.haptic) "已启用 · 轻触" else "触发动作时震动",
            checked = state.haptic,
            onCheckedChange = callbacks.setHaptic,
            icon = EdgeXIcons.Gesture,
        )
        EdgeXDivider()
        EdgeXSwitchRow(
            title = "弧形冰箱",
            subtitle = "右侧弧形抽屉显示应用",
            checked = state.arcDrawer,
            onCheckedChange = callbacks.setArcDrawer,
            icon = EdgeXIcons.Freeze,
        )
        EdgeXDivider()
        EdgeXRow(
            title = "重启 SystemUI",
            subtitle = "应用部分系统级更改",
            icon = EdgeXIcons.Sparkle,
            onClick = { callbacks.showToast("正在重启 SystemUI…") },
        ) {
            EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim)
        }
    }
}

@Composable
private fun AboutSettings(callbacks: HomeCallbacks) {
    EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
        EdgeXRow(
            title = "支持作者",
            subtitle = "支付宝 · 微信 · ETH · SOL",
            icon = EdgeXIcons.Sparkle,
            onClick = { callbacks.openRoute(EdgeXRoute.Premium) },
        ) {
            EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim)
        }
        EdgeXDivider()
        EdgeXRow(
            title = "EdgeX v0.1",
            subtitle = "github.com/fcmfcm1999/EdgeX",
            icon = EdgeXIcons.Gesture,
            onClick = { callbacks.openRoute(EdgeXRoute.About) },
        ) {
            EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim)
        }
    }
}
