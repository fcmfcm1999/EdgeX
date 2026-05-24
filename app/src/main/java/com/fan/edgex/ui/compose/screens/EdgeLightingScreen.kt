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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.R
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
    val labelRes: Int,
)

private val edgeLightingEffects = listOf(
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_BASIC, R.string.edge_lighting_effect_basic),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_BREATHING, R.string.edge_lighting_effect_breathing),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_FLOW, R.string.edge_lighting_effect_flow),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_MULTICOLOR, R.string.edge_lighting_effect_multicolor),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_SPOTLIGHT, R.string.edge_lighting_effect_spotlight),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_ECLIPSE, R.string.edge_lighting_effect_eclipse),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_ECHO, R.string.edge_lighting_effect_echo),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_COMET, R.string.edge_lighting_effect_comet),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_RIPPLE, R.string.edge_lighting_effect_ripple),
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
            title = stringResource(R.string.header_edge_lighting),
            onBack = onBack,
            trailing = {
                EdgeXIconButton(onClick = { showToast(context.getString(R.string.header_edge_lighting)) }) {
                    EdgeXIcon(EdgeXIcons.Info, contentDescription = stringResource(R.string.compose_info), tint = LocalEdgeXColors.current.onSurface)
                }
            },
        )
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.compose_edge_lighting_hero),
                color = LocalEdgeXColors.current.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 31.sp,
            )
        }
        EdgeLightingPreview(selectedEffect = selectedEffect, enabled = enabled)
        EdgeLightingSectionLabel(stringResource(R.string.edge_lighting_section_general))
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            EdgeXSwitchRow(
                title = stringResource(R.string.compose_edge_lighting_enabled),
                subtitle = if (enabled) stringResource(R.string.compose_edge_lighting_enabled_desc) else stringResource(R.string.compose_edge_lighting_disabled_desc),
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    context.putConfig(AppConfig.EDGE_LIGHTING_ENABLED, it)
                    showToast(context.getString(if (it) R.string.compose_edge_lighting_enabled_toast else R.string.compose_edge_lighting_disabled_toast))
                },
                icon = EdgeXIcons.Sparkle,
            )
            EdgeXDivider()
            EdgeXSwitchRow(
                title = stringResource(R.string.compose_edge_lighting_auto_color),
                subtitle = stringResource(R.string.compose_edge_lighting_auto_color_desc),
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
                showToast(context.getString(R.string.compose_selected_toast, context.getString(it.labelRes)))
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
        EdgeLightingSectionLabel(stringResource(R.string.edge_lighting_effect), compact = true)
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
            text = stringResource(effect.labelRes),
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
