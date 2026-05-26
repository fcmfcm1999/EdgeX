package com.fan.edgex.ui.compose.screens

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.license.PremiumActivator
import com.fan.edgex.overlay.EdgeLightingView
import com.fan.edgex.service.NotificationEdgeService
import com.fan.edgex.ui.EdgeLightingAppFilterActivity
import com.fan.edgex.ui.compose.components.EdgeXChip
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.components.PhoneFrame
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private data class EdgeLightingEffect(
    val code: String,
    val labelRes: Int,
)

private val edgeLightingEffects = listOf(
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_BASIC, R.string.edge_lighting_effect_basic),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_BREATHING, R.string.edge_lighting_effect_breathing),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_COMET, R.string.edge_lighting_effect_comet),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_FLOW, R.string.edge_lighting_effect_flow),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_MULTICOLOR, R.string.edge_lighting_effect_multicolor),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_SPOTLIGHT, R.string.edge_lighting_effect_spotlight),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_ECLIPSE, R.string.edge_lighting_effect_eclipse),
    EdgeLightingEffect(AppConfig.EDGE_LIGHTING_EFFECT_ECHO, R.string.edge_lighting_effect_echo),
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
    var selectedEffect by remember { mutableStateOf(context.getConfigString(AppConfig.EDGE_LIGHTING_EFFECT, AppConfig.EDGE_LIGHTING_EFFECT_BASIC)) }
    var color by remember { mutableStateOf(parseColor(context.getConfigString(AppConfig.EDGE_LIGHTING_COLOR, DEFAULT_COLOR))) }
    var widthDp by remember { mutableIntStateOf(context.getConfigString(AppConfig.EDGE_LIGHTING_WIDTH_DP, "5").toIntOrNull()?.coerceIn(1, 20) ?: 5) }
    var durationMs by remember { mutableIntStateOf(context.getConfigString(AppConfig.EDGE_LIGHTING_DURATION_MS, "3000").toIntOrNull()?.coerceIn(500, 10000) ?: 3000) }
    var alphaPct by remember {
        mutableIntStateOf(((context.getConfigString(AppConfig.EDGE_LIGHTING_ALPHA, "1.0").toFloatOrNull() ?: 1f) * 100).roundToInt().coerceIn(0, 100))
    }
    var notificationAccessGranted by remember { mutableStateOf(context.isNotificationAccessGranted()) }
    var selectedAppCount by remember { mutableIntStateOf(parsePackageList(context.getConfigString(AppConfig.EDGE_LIGHTING_APP_LIST)).size) }
    val premiumActive = PremiumActivator.status(context) != PremiumActivator.Status.NotActivated
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshExternalState() {
        notificationAccessGranted = context.isNotificationAccessGranted()
        selectedAppCount = parsePackageList(context.getConfigString(AppConfig.EDGE_LIGHTING_APP_LIST)).size
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshExternalState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = stringResource(R.string.header_edge_lighting), onBack = onBack)
        EdgeLightingPreview(
            effect = selectedEffect,
            color = color,
            widthDp = widthDp,
            alphaPct = alphaPct,
            enabled = enabled,
        )
        EdgeLightingSectionLabel(stringResource(R.string.edge_lighting_effect))
        EffectChips(
            selected = selectedEffect,
            onSelected = {
                selectedEffect = it.code
                context.putConfig(AppConfig.EDGE_LIGHTING_EFFECT, it.code)
                showToast(context.getString(R.string.compose_selected_toast, context.getString(it.labelRes)))
            },
        )
        EdgeLightingSectionLabel(stringResource(R.string.edge_lighting_section_general))
        GeneralSettings(
            enabled = enabled,
            autoColor = autoColor,
            notificationAccessGranted = notificationAccessGranted,
            selectedAppCount = selectedAppCount,
            premiumActive = premiumActive,
            onEnabledChange = {
                enabled = it
                context.putConfig(AppConfig.EDGE_LIGHTING_ENABLED, it)
                showToast(context.getString(if (it) R.string.compose_edge_lighting_enabled_toast else R.string.compose_edge_lighting_disabled_toast))
            },
            onAutoColorChange = {
                autoColor = it
                context.putConfig(AppConfig.EDGE_LIGHTING_AUTO_COLOR, it)
            },
            onNotificationAccess = {
                if (premiumActive) context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onApps = {
                if (premiumActive) context.startActivity(Intent(context, EdgeLightingAppFilterActivity::class.java))
            },
        )
        EdgeLightingSectionLabel(stringResource(R.string.edge_lighting_section_color))
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            EdgeXRow(
                title = "#%06X".format(0xFFFFFF and color),
                subtitle = stringResource(R.string.edge_lighting_color),
                icon = EdgeXIcons.Theme,
                onClick = {
                    ColorPickerDialog.show(
                        context = context,
                        title = context.getString(R.string.edge_lighting_color),
                        configKey = AppConfig.EDGE_LIGHTING_COLOR,
                        defaultColor = DEFAULT_COLOR,
                    ) { picked -> color = picked }
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(color)),
                )
            }
        }
        EdgeLightingSectionLabel(stringResource(R.string.edge_lighting_section_appearance))
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            ConfigSlider(
                label = stringResource(R.string.edge_lighting_label_width),
                valueText = stringResource(R.string.edge_lighting_width_dp, widthDp),
                value = widthDp,
                range = 1..20,
                onValue = {
                    widthDp = it
                    context.putConfig(AppConfig.EDGE_LIGHTING_WIDTH_DP, it.toString())
                },
            )
            EdgeXDivider()
            ConfigSlider(
                label = stringResource(R.string.edge_lighting_label_opacity),
                valueText = stringResource(R.string.edge_lighting_alpha_pct, alphaPct),
                value = alphaPct,
                range = 0..100,
                onValue = {
                    alphaPct = it
                    context.putConfig(AppConfig.EDGE_LIGHTING_ALPHA, (it / 100f).toString())
                },
            )
        }
        EdgeLightingSectionLabel(stringResource(R.string.edge_lighting_section_timing))
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            ConfigSlider(
                label = stringResource(R.string.edge_lighting_label_duration),
                valueText = stringResource(R.string.edge_lighting_duration_ms, durationMs),
                value = durationMs,
                range = 500..10000,
                step = 100,
                onValue = {
                    durationMs = it
                    context.putConfig(AppConfig.EDGE_LIGHTING_DURATION_MS, it.toString())
                },
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun EdgeLightingPreview(
    effect: String,
    color: Int,
    widthDp: Int,
    alphaPct: Int,
    enabled: Boolean,
) {
    val colors = LocalEdgeXColors.current
    val density = LocalContext.current.resources.displayMetrics.density
    val effectLabel = edgeLightingEffects.firstOrNull { it.code == effect }?.let { stringResource(it.labelRes) }.orEmpty()
    var previewView by remember { mutableStateOf<EdgeLightingView?>(null) }

    DisposableEffect(previewView, effect, alphaPct) {
        val view = previewView ?: return@DisposableEffect onDispose { }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                val progress = it.animatedValue as Float
                view.flowProgress = progress
                val baseAlpha = if (enabled) alphaPct / 100f else 0f
                view.glowAlpha = if (effect == AppConfig.EDGE_LIGHTING_EFFECT_BREATHING) {
                    val pulse = 0.4f + 0.6f * ((sin(progress * PI * 4.0) + 1.0) / 2.0).toFloat()
                    baseAlpha * pulse
                } else {
                    baseAlpha
                }
            }
            start()
        }
        onDispose { animator.cancel() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        PhoneFrame {
            AndroidView(
                factory = { context ->
                    EdgeLightingView(context).also { previewView = it }
                },
                update = { view ->
                    previewView = view
                    view.glowColor = color
                    view.glowWidthPx = widthDp.coerceIn(1, 20) * density
                    view.glowAlpha = if (enabled) alphaPct / 100f else 0f
                    view.effect = effect
                },
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 30.dp)
                    .width(144.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.86f))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(color)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.edge_lighting_preview_notification_title),
                        color = Color(0xE6000000),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.edge_lighting_preview_notification_body),
                        color = Color(0x99000000),
                        fontSize = 8.sp,
                        lineHeight = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = effectLabel,
            color = colors.onSurfaceDim,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun EffectChips(selected: String, onSelected: (EdgeLightingEffect) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        edgeLightingEffects.forEach { effect ->
            EdgeXChip(
                label = stringResource(effect.labelRes),
                selected = selected == effect.code,
                onClick = { onSelected(effect) },
            )
        }
    }
}

@Composable
private fun GeneralSettings(
    enabled: Boolean,
    autoColor: Boolean,
    notificationAccessGranted: Boolean,
    selectedAppCount: Int,
    premiumActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onAutoColorChange: (Boolean) -> Unit,
    onNotificationAccess: () -> Unit,
    onApps: () -> Unit,
) {
    val colors = LocalEdgeXColors.current
    EdgeXListGroup(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .alpha(if (premiumActive) 1f else DISABLED_ALPHA),
    ) {
        EdgeXSwitchRow(
            title = stringResource(R.string.edge_lighting_enabled),
            checked = enabled,
            onCheckedChange = { if (premiumActive) onEnabledChange(it) },
            icon = EdgeXIcons.EdgeLighting,
        )
        EdgeXDivider()
        EdgeXSwitchRow(
            title = stringResource(R.string.edge_lighting_auto_color),
            checked = autoColor,
            onCheckedChange = { if (premiumActive) onAutoColorChange(it) },
            icon = EdgeXIcons.Theme,
        )
        EdgeXDivider()
        EdgeXRow(
            title = stringResource(R.string.edge_lighting_notification_access_title),
            subtitle = stringResource(if (notificationAccessGranted) R.string.edge_lighting_notif_status_granted else R.string.edge_lighting_notif_status_required),
            icon = EdgeXIcons.Notifications,
            onClick = if (premiumActive) onNotificationAccess else null,
        ) {
            Text(
                text = if (notificationAccessGranted) "OK" else "!",
                color = if (notificationAccessGranted) colors.accent else colors.danger,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
        EdgeXDivider()
        EdgeXRow(
            title = stringResource(R.string.edge_lighting_app_filter),
            subtitle = if (selectedAppCount == 0) {
                stringResource(R.string.edge_lighting_app_filter_all)
            } else {
                stringResource(R.string.edge_lighting_app_filter_selected, selectedAppCount)
            },
            icon = EdgeXIcons.Apps,
            onClick = if (premiumActive) onApps else null,
        ) {
            EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim)
        }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onValue: (Int) -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(valueText, color = colors.onSurfaceDim, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                val stepped = ((it.roundToInt() - range.first) / step) * step + range.first
                onValue(stepped.coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surface2,
            ),
        )
    }
}

@Composable
private fun EdgeLightingSectionLabel(label: String) {
    Text(
        text = label,
        color = LocalEdgeXColors.current.onSurfaceDim,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 10.dp),
    )
}

private fun parseColor(value: String): Int =
    runCatching { android.graphics.Color.parseColor(value) }.getOrElse { android.graphics.Color.CYAN }

private fun parsePackageList(value: String): Set<String> {
    if (value.isBlank()) return emptySet()
    return runCatching {
        val array = JSONArray(value)
        buildSet {
            for (index in 0 until array.length()) {
                val packageName = array.optString(index).trim()
                if (packageName.isNotEmpty()) add(packageName)
            }
        }
    }.getOrElse {
        value.split(",").mapNotNullTo(mutableSetOf()) { it.trim().takeIf(String::isNotEmpty) }
    }
}

private fun Context.isNotificationAccessGranted(): Boolean {
    val componentName = ComponentName(this, NotificationEdgeService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
    return enabled.split(':').any { it.equals(componentName, ignoreCase = true) }
}

private const val DEFAULT_COLOR = "#00FFFF"
private const val DISABLED_ALPHA = 0.45f
