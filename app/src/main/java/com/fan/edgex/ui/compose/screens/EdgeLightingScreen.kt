package com.fan.edgex.ui.compose.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconButton
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

private data class EdgeLightingEffect(
    val code: String,
    val label: String,
)

private val edgeLightingEffects = listOf(
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_BASIC, "基础光晕"),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_BREATHING, "呼吸"),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_FLOW, "流光"),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_MULTICOLOR, "多彩"),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_SPOTLIGHT, "聚光"),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_ECLIPSE, "月蚀"),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_ECHO, "回声"),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_COMET, "彗星"),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_RIPPLE, "涟漪"),
)

@Composable
fun EdgeLightingScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(context.getConfigBool(AppConfig.EDGE_LIGHTING_ENABLED, default = true)) }
    var autoColor by remember { mutableStateOf(context.getConfigBool(AppConfig.EDGE_LIGHTING_AUTO_COLOR, default = true)) }
    var selectedEffect by remember {
        mutableStateOf(context.getConfigString(AppConfig.EDGE_LIGHTING_EFFECT, AppConfig.EDGE_LIGHTING_EFFECT_BASIC))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(
            title = "Edge Lighting",
            onBack = onBack,
            trailing = {
                EdgeXIconButton(onClick = { showToast("Edge Lighting") }) {
                    EdgeXIcon(EdgeXIcons.Check, contentDescription = "信息", tint = LocalEdgeXColors.current.onSurface)
                }
            },
        )
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = "通知亮起时\n屏幕开始呼吸",
                color = LocalEdgeXColors.current.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 31.sp,
            )
        }
        EdgeLightingPreview(selectedEffect = selectedEffect, enabled = enabled)
        EdgeLightingSectionLabel("通用")
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            EdgeXSwitchRow(
                title = "启用 Edge Lighting",
                subtitle = if (enabled) "通知监听服务将显示边缘光效" else "不会显示通知边缘光效",
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    context.putConfig(AppConfig.EDGE_LIGHTING_ENABLED, it)
                    showToast(if (it) "Edge Lighting 已启用" else "Edge Lighting 已停用")
                },
                icon = EdgeXIcons.Sparkle,
            )
            EdgeXDivider()
            EdgeXSwitchRow(
                title = "自动使用通知颜色",
                subtitle = "优先从通知图标或应用色彩取色",
                checked = autoColor,
                onCheckedChange = {
                    autoColor = it
                    context.putConfig(AppConfig.EDGE_LIGHTING_AUTO_COLOR, it)
                },
                icon = EdgeXIcons.Theme,
            )
        }
        EffectGrid(
            selected = selectedEffect,
            onSelected = {
                selectedEffect = it.code
                context.putConfig(AppConfig.EDGE_LIGHTING_EFFECT, it.code)
                showToast("已选择: ${it.label}")
            },
        )
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun EdgeLightingPreview(selectedEffect: String, enabled: Boolean) {
    val colors = LocalEdgeXColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF0A0B07))
            .border(4.dp, previewBrush(selectedEffect, enabled), RoundedCornerShape(28.dp))
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1D2018))
                .border(1.dp, colors.accent.copy(alpha = if (enabled) 0.45f else 0.12f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.accent.copy(alpha = if (enabled) 0.08f else 0.02f)),
            )
        }
    }
}

@Composable
private fun EffectGrid(selected: String, onSelected: (EdgeLightingEffect) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EdgeLightingSectionLabel("特效", compact = true)
        edgeLightingEffects.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { effect ->
                    EffectTile(
                        effect = effect,
                        selected = selected == effect.code,
                        onClick = { onSelected(effect) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EdgeLightingSectionLabel(label: String, compact: Boolean = false) {
    Text(
        text = label,
        color = LocalEdgeXColors.current.onSurfaceDim,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = if (compact) 0.dp else 24.dp, end = 24.dp, top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun EffectTile(
    effect: EdgeLightingEffect,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    Column(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(EdgeXRadius.md))
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(
                width = 1.dp,
                color = if (selected) colors.accent else colors.outline,
                shape = RoundedCornerShape(EdgeXRadius.md),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(previewBrush(effect.code, enabled = true)),
        )
        Text(
            text = effect.label,
            color = if (selected) colors.onAccentSoft else colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun previewBrush(effect: String, enabled: Boolean): Brush {
    val colors = LocalEdgeXColors.current
    if (!enabled) {
        return Brush.linearGradient(listOf(colors.surface2, colors.surface3))
    }
    return when (effect) {
        AppConfig.EDGE_LIGHTING_EFFECT_MULTICOLOR -> Brush.linearGradient(
            listOf(Color(0xFF3B6CE5), Color(0xFF8AD995), Color(0xFFF0978A), Color(0xFFEFC56D)),
        )
        AppConfig.EDGE_LIGHTING_EFFECT_ECLIPSE -> Brush.linearGradient(listOf(colors.surface3, colors.accent, colors.surface3))
        AppConfig.EDGE_LIGHTING_EFFECT_COMET -> Brush.linearGradient(listOf(colors.accent, Color.Transparent, colors.accentSoft))
        AppConfig.EDGE_LIGHTING_EFFECT_RIPPLE -> Brush.radialGradient(listOf(colors.accent, colors.accentSoft, colors.surface1))
        AppConfig.EDGE_LIGHTING_EFFECT_SPOTLIGHT -> Brush.radialGradient(listOf(colors.accent, colors.surface1))
        else -> Brush.linearGradient(listOf(colors.accentSoft, colors.accent, colors.accentSoft2))
    }
}
