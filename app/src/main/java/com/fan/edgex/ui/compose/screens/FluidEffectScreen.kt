package com.fan.edgex.ui.compose.screens

import android.animation.ValueAnimator
import android.graphics.Color
import android.view.animation.LinearInterpolator
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.license.PremiumActivator
import com.fan.edgex.ui.ColorPickerView
import com.fan.edgex.ui.FluidEffectPreviewView
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.components.PhoneFrame
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import kotlin.math.roundToInt

private data class FluidColorRow(
    val configKey: String,
    val labelRes: Int,
    val allowReset: Boolean,
)

private val fluidColorRows = listOf(
    FluidColorRow(AppConfig.FLUID_EFFECT_COLOR, R.string.fluid_effect_color_global, allowReset = false),
    FluidColorRow(AppConfig.FLUID_EFFECT_COLOR_LEFT, R.string.fluid_effect_color_left, allowReset = true),
    FluidColorRow(AppConfig.FLUID_EFFECT_COLOR_RIGHT, R.string.fluid_effect_color_right, allowReset = true),
    FluidColorRow(AppConfig.FLUID_EFFECT_COLOR_TOP, R.string.fluid_effect_color_top, allowReset = true),
    FluidColorRow(AppConfig.FLUID_EFFECT_COLOR_BOTTOM, R.string.fluid_effect_color_bottom, allowReset = true),
)

@Composable
fun FluidEffectScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val premiumActive = PremiumActivator.status(context) != PremiumActivator.Status.NotActivated
    var enabled by remember { mutableStateOf(context.getConfigBool(AppConfig.FLUID_EFFECT_ENABLED, default = true)) }
    var globalColor by remember { mutableStateOf(parseFluidColor(context.getConfigString(AppConfig.FLUID_EFFECT_COLOR, DEFAULT_COLOR))) }
    var leftColor by remember { mutableStateOf(readEdgeColor(context, AppConfig.FLUID_EFFECT_COLOR_LEFT)) }
    var rightColor by remember { mutableStateOf(readEdgeColor(context, AppConfig.FLUID_EFFECT_COLOR_RIGHT)) }
    var topColor by remember { mutableStateOf(readEdgeColor(context, AppConfig.FLUID_EFFECT_COLOR_TOP)) }
    var bottomColor by remember { mutableStateOf(readEdgeColor(context, AppConfig.FLUID_EFFECT_COLOR_BOTTOM)) }
    var sizeProgress by remember {
        mutableIntStateOf(context.getConfigString(AppConfig.FLUID_EFFECT_SIZE, AppConfig.FLUID_EFFECT_SIZE_DEFAULT.toString()).toIntOrNull()?.coerceIn(0, 100) ?: AppConfig.FLUID_EFFECT_SIZE_DEFAULT)
    }
    var alphaPct by remember {
        mutableIntStateOf(((context.getConfigString(AppConfig.FLUID_EFFECT_ALPHA, "0.8").toFloatOrNull() ?: 0.8f) * 100).roundToInt().coerceIn(10, 100))
    }
    var colorVersion by remember { mutableIntStateOf(0) }

    fun edgeColor(key: String): Int? =
        when (key) {
            AppConfig.FLUID_EFFECT_COLOR_LEFT -> leftColor
            AppConfig.FLUID_EFFECT_COLOR_RIGHT -> rightColor
            AppConfig.FLUID_EFFECT_COLOR_TOP -> topColor
            AppConfig.FLUID_EFFECT_COLOR_BOTTOM -> bottomColor
            else -> null
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = stringResource(R.string.header_fluid_effect), onBack = onBack)
        FluidEffectPreview(
            globalColor = globalColor,
            leftColor = leftColor,
            rightColor = rightColor,
            topColor = topColor,
            bottomColor = bottomColor,
            sizeProgress = sizeProgress,
            alphaPct = alphaPct,
            enabled = enabled,
            colorVersion = colorVersion,
        )
        FluidSectionLabel(stringResource(R.string.fluid_effect_section_general))
        EdgeXListGroup(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .alpha(if (premiumActive) 1f else DISABLED_ALPHA),
        ) {
            EdgeXSwitchRow(
                title = stringResource(R.string.fluid_effect_enabled),
                checked = enabled,
                onCheckedChange = {
                    if (premiumActive) {
                        enabled = it
                        context.putConfig(AppConfig.FLUID_EFFECT_ENABLED, it)
                    }
                },
                icon = EdgeXIcons.FluidEffect,
            )
        }
        FluidSectionLabel(stringResource(R.string.fluid_effect_section_color))
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            fluidColorRows.forEachIndexed { index, row ->
                if (index > 0) EdgeXDivider()
                val stored = context.getConfigString(row.configKey)
                val displayColor = if (row.allowReset && stored.isBlank()) globalColor else parseFluidColor(stored.ifBlank { DEFAULT_COLOR })
                EdgeXRow(
                    title = stringResource(row.labelRes),
                    icon = EdgeXIcons.Theme,
                    onClick = {
                        if (premiumActive) showFluidColorPicker(
                            context = context,
                            title = context.getString(row.labelRes),
                            configKey = row.configKey,
                            allowReset = row.allowReset,
                            onSaved = {
                                if (row.configKey == AppConfig.FLUID_EFFECT_COLOR) {
                                    for (r in fluidColorRows) {
                                        if (r.allowReset) context.putConfig(r.configKey, "")
                                    }
                                }
                                globalColor = parseFluidColor(context.getConfigString(AppConfig.FLUID_EFFECT_COLOR, DEFAULT_COLOR))
                                leftColor = readEdgeColor(context, AppConfig.FLUID_EFFECT_COLOR_LEFT)
                                rightColor = readEdgeColor(context, AppConfig.FLUID_EFFECT_COLOR_RIGHT)
                                topColor = readEdgeColor(context, AppConfig.FLUID_EFFECT_COLOR_TOP)
                                bottomColor = readEdgeColor(context, AppConfig.FLUID_EFFECT_COLOR_BOTTOM)
                                colorVersion++
                            },
                        )
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ComposeColor(displayColor))
                            .border(1.dp, LocalEdgeXColors.current.onSurface.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
        FluidSectionLabel(stringResource(R.string.fluid_effect_section_appearance))
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            FluidSlider(
                label = stringResource(R.string.fluid_effect_size),
                valueText = elasticityForProgress(sizeProgress),
                value = sizeProgress,
                range = 0..100,
                onValue = {
                    sizeProgress = it
                    context.putConfig(AppConfig.FLUID_EFFECT_SIZE, it.toString())
                },
            )
            EdgeXDivider()
            FluidSlider(
                label = stringResource(R.string.fluid_effect_alpha),
                valueText = stringResource(R.string.fluid_effect_alpha_value, alphaPct),
                value = alphaPct,
                range = 10..100,
                onValue = {
                    alphaPct = it
                    context.putConfig(AppConfig.FLUID_EFFECT_ALPHA, (it / 100f).toString())
                },
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun FluidEffectPreview(
    globalColor: Int,
    leftColor: Int?,
    rightColor: Int?,
    topColor: Int?,
    bottomColor: Int?,
    sizeProgress: Int,
    alphaPct: Int,
    enabled: Boolean,
    colorVersion: Int,
) {
    val colors = LocalEdgeXColors.current
    var previewView by remember { mutableStateOf<FluidEffectPreviewView?>(null) }

    DisposableEffect(previewView) {
        val view = previewView ?: return@DisposableEffect onDispose { }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { view.progress = it.animatedValue as Float }
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
                factory = { ctx ->
                    FluidEffectPreviewView(ctx).also { previewView = it }
                },
                update = { view ->
                    previewView = view
                    view.globalColor = globalColor
                    view.leftColor = leftColor
                    view.rightColor = rightColor
                    view.topColor = topColor
                    view.bottomColor = bottomColor
                    view.sizeProgress = sizeProgress
                    view.maxAlpha = if (enabled) alphaPct / 100f else 0f
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FluidSlider(
    label: String,
    valueText: String,
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(valueText, color = colors.onSurfaceDim, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        androidx.compose.material3.Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surface2,
            ),
        )
    }
}

@Composable
private fun FluidSectionLabel(label: String) {
    Text(
        text = label,
        color = LocalEdgeXColors.current.onSurfaceDim,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 10.dp),
    )
}

private fun showFluidColorPicker(
    context: android.content.Context,
    title: String,
    configKey: String,
    allowReset: Boolean,
    onSaved: () -> Unit,
) {
    val dialogView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
    val picker = dialogView.findViewById<ColorPickerView>(R.id.color_picker_view)
    val preview = dialogView.findViewById<android.view.View>(R.id.preview_swatch)
    val hex = dialogView.findViewById<EditText>(R.id.et_hex)
    val red = dialogView.findViewById<EditText>(R.id.et_r)
    val green = dialogView.findViewById<EditText>(R.id.et_g)
    val blue = dialogView.findViewById<EditText>(R.id.et_b)
    val alpha = dialogView.findViewById<EditText>(R.id.et_a)

    val stored = context.getConfigString(configKey).ifBlank {
        context.getConfigString(AppConfig.FLUID_EFFECT_COLOR, DEFAULT_COLOR)
    }
    picker.setColor(parseFluidColor(stored))

    var isUpdating = false

    fun syncUi(color: Int) {
        if (isUpdating) return
        isUpdating = true
        preview.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 8f * context.resources.displayMetrics.density
            setColor(color)
            setStroke(
                (1f * context.resources.displayMetrics.density).toInt(),
                ContextCompat.getColor(context, R.color.ui_divider),
            )
        }
        val colorHex = displayFluidColor(color)
        if (hex.text.toString() != colorHex) hex.setText(colorHex)
        setTextIfChanged(red, Color.red(color).toString())
        setTextIfChanged(green, Color.green(color).toString())
        setTextIfChanged(blue, Color.blue(color).toString())
        setTextIfChanged(alpha, Color.alpha(color).toString())
        isUpdating = false
    }

    syncUi(picker.getColor())
    picker.onColorChanged = { syncUi(it) }

    hex.addTextChangedListener(simpleWatcher { value ->
        if (!isUpdating && value.length == 8) {
            runCatching { java.lang.Long.parseLong(value, 16).toInt() }.onSuccess {
                picker.setColor(it)
                syncUi(it)
            }
        }
    })

    fun componentWatcher(component: Char) = simpleWatcher { value ->
        if (isUpdating) return@simpleWatcher
        val componentValue = value.toIntOrNull()?.coerceIn(0, 255) ?: return@simpleWatcher
        val color = picker.getColor()
        val nextColor = when (component) {
            'r' -> Color.argb(Color.alpha(color), componentValue, Color.green(color), Color.blue(color))
            'g' -> Color.argb(Color.alpha(color), Color.red(color), componentValue, Color.blue(color))
            'b' -> Color.argb(Color.alpha(color), Color.red(color), Color.green(color), componentValue)
            'a' -> Color.argb(componentValue, Color.red(color), Color.green(color), Color.blue(color))
            else -> color
        }
        picker.setColor(nextColor)
        syncUi(nextColor)
    }

    red.addTextChangedListener(componentWatcher('r'))
    green.addTextChangedListener(componentWatcher('g'))
    blue.addTextChangedListener(componentWatcher('b'))
    alpha.addTextChangedListener(componentWatcher('a'))

    val builder = AlertDialog.Builder(context)
        .setTitle(title)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            context.putConfig(configKey, displayFluidColor(picker.getColor()))
            onSaved()
        }
        .setNegativeButton(android.R.string.cancel, null)

    if (allowReset) {
        builder.setNeutralButton(R.string.fluid_effect_color_default) { _, _ ->
            context.putConfig(configKey, "")
            onSaved()
        }
    }

    builder.show()
}

private fun readEdgeColor(context: android.content.Context, key: String): Int? {
    val stored = context.getConfigString(key)
    return stored.takeIf(String::isNotBlank)?.let { parseFluidColor(it) }
}

private fun parseFluidColor(value: String): Int {
    val normalized = value.removePrefix("#").ifBlank { DEFAULT_COLOR }
    return runCatching {
        when (normalized.length) {
            6 -> Color.rgb(
                normalized.substring(0, 2).toInt(16),
                normalized.substring(2, 4).toInt(16),
                normalized.substring(4, 6).toInt(16),
            )
            8 -> java.lang.Long.parseLong(normalized, 16).toInt()
            else -> Color.parseColor("#$DEFAULT_COLOR")
        }
    }.getOrDefault(Color.parseColor("#$DEFAULT_COLOR"))
}

private fun displayFluidColor(color: Int): String =
    "%08X".format(color.toLong() and 0xFFFFFFFFL)

private fun elasticityForProgress(progress: Int): String =
    "%.1fx".format(0.3f + progress.coerceIn(0, 100) * 0.027f)

private fun simpleWatcher(onChanged: (String) -> Unit) = object : android.text.TextWatcher {
    override fun afterTextChanged(s: android.text.Editable) = onChanged(s.toString())
    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
}

private fun setTextIfChanged(editText: EditText, value: String) {
    if (editText.text.toString() != value) editText.setText(value)
}

private const val DEFAULT_COLOR = "CCFFFFFF"
private const val DISABLED_ALPHA = 0.45f
