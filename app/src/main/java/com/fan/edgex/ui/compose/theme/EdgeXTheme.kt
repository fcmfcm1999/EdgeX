package com.fan.edgex.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigString

enum class EdgeXAccent(
    val id: String,
    val lightAccent: Color,
    val lightAccentPress: Color,
    val lightAccentSoft: Color,
    val lightAccentSoft2: Color,
    val lightOnAccentSoft: Color,
    val darkAccent: Color,
    val darkAccentPress: Color,
    val darkAccentSoft: Color,
    val darkAccentSoft2: Color,
    val darkOnAccentSoft: Color,
) {
    Default(
        id = "default",
        lightAccent = Color(0xFF326D32),
        lightAccentPress = Color(0xFF275A27),
        lightAccentSoft = Color(0xFFC8E6C9),
        lightAccentSoft2 = Color(0xFFDCEEDD),
        lightOnAccentSoft = Color(0xFF0D3B0D),
        darkAccent = Color(0xFF8BC88B),
        darkAccentPress = Color(0xFF6FB86F),
        darkAccentSoft = Color(0xFF1F4A1F),
        darkAccentSoft2 = Color(0xFF143014),
        darkOnAccentSoft = Color(0xFFC8E6C9),
    ),
    Classic(
        id = "classic",
        lightAccent = Color(0xFF00796B),
        lightAccentPress = Color(0xFF006055),
        lightAccentSoft = Color(0xFFB2DFDB),
        lightAccentSoft2 = Color(0xFFCCF0ED),
        lightOnAccentSoft = Color(0xFF003D35),
        darkAccent = Color(0xFF80CBC4),
        darkAccentPress = Color(0xFF66B8B0),
        darkAccentSoft = Color(0xFF1A4A44),
        darkAccentSoft2 = Color(0xFF10302C),
        darkOnAccentSoft = Color(0xFFB2DFDB),
    ),
    Cedar(
        id = "cedar",
        lightAccent = Color(0xFF496B3D),
        lightAccentPress = Color(0xFF3A5830),
        lightAccentSoft = Color(0xFFD5E6CC),
        lightAccentSoft2 = Color(0xFFE5F0DF),
        lightOnAccentSoft = Color(0xFF1A3014),
        darkAccent = Color(0xFF9DB893),
        darkAccentPress = Color(0xFF84A87A),
        darkAccentSoft = Color(0xFF243D1F),
        darkAccentSoft2 = Color(0xFF182914),
        darkOnAccentSoft = Color(0xFFD5E6CC),
    ),
    Ocean(
        id = "ocean",
        lightAccent = Color(0xFF2F6F8F),
        lightAccentPress = Color(0xFF245A74),
        lightAccentSoft = Color(0xFFC5DCE8),
        lightAccentSoft2 = Color(0xFFDAEAF3),
        lightOnAccentSoft = Color(0xFF0A3045),
        darkAccent = Color(0xFF8AB8D4),
        darkAccentPress = Color(0xFF6FA8C8),
        darkAccentSoft = Color(0xFF1A3D52),
        darkAccentSoft2 = Color(0xFF102838),
        darkOnAccentSoft = Color(0xFFC5DCE8),
    ),
    Ember(
        id = "ember",
        lightAccent = Color(0xFFC56B2A),
        lightAccentPress = Color(0xFFA8571E),
        lightAccentSoft = Color(0xFFF5D8BF),
        lightAccentSoft2 = Color(0xFFF9E6D4),
        lightOnAccentSoft = Color(0xFF522408),
        darkAccent = Color(0xFFE8A66A),
        darkAccentPress = Color(0xFFDD9050),
        darkAccentSoft = Color(0xFF4A2E10),
        darkAccentSoft2 = Color(0xFF301D08),
        darkOnAccentSoft = Color(0xFFF5D8BF),
    ),
    Custom(
        id = "custom",
        lightAccent = Color(0xFF326D32),
        lightAccentPress = Color(0xFF275A27),
        lightAccentSoft = Color(0xFFC8E6C9),
        lightAccentSoft2 = Color(0xFFDCEEDD),
        lightOnAccentSoft = Color(0xFF0D3B0D),
        darkAccent = Color(0xFF8BC88B),
        darkAccentPress = Color(0xFF6FB86F),
        darkAccentSoft = Color(0xFF1F4A1F),
        darkAccentSoft2 = Color(0xFF143014),
        darkOnAccentSoft = Color(0xFFC8E6C9),
    );

    companion object {
        fun fromId(id: String?): EdgeXAccent =
            entries.firstOrNull { it.id == id } ?: Default
    }
}

@Immutable
data class EdgeXColors(
    val bg: Color,
    val surface: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surfaceTint: Color,
    val outline: Color,
    val outlineStrong: Color,
    val onSurface: Color,
    val onSurface2: Color,
    val onSurfaceDim: Color,
    val onSurfaceFaint: Color,
    val accent: Color,
    val accentPress: Color,
    val accentSoft: Color,
    val accentSoft2: Color,
    val onAccent: Color,
    val onAccentSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val danger: Color,
)

object EdgeXRadius {
    val xs = 8.dp
    val sm = 14.dp
    val md = 22.dp
    val lg = 28.dp
    val xl = 36.dp
}

val LocalEdgeXColors = staticCompositionLocalOf { lightEdgeXColors(EdgeXAccent.Default) }

@Composable
fun EdgeXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: EdgeXAccent = EdgeXAccent.Default,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val customColorHex = if (accent == EdgeXAccent.Custom) {
        context.getConfigString(AppConfig.THEME_CUSTOM_COLOR, "#326D32")
    } else ""

    val colors = androidx.compose.runtime.remember(darkTheme, accent, customColorHex) {
        if (accent == EdgeXAccent.Custom) {
            val baseColor = runCatching { Color(android.graphics.Color.parseColor(customColorHex)) }
                .getOrDefault(Color(0xFF326D32))
            if (darkTheme) {
                val generated = getDynamicDarkAccentColors(baseColor)
                EdgeXColors(
                    bg = Color(0xFF0E110A),
                    surface = Color(0xFF181C12),
                    surface1 = Color(0xFF1F2418),
                    surface2 = Color(0xFF272D1F),
                    surface3 = Color(0xFF313826),
                    surfaceTint = Color(0xFF3D4630),
                    outline = Color(0x14FFFFFF),
                    outlineStrong = Color(0x2EFFFFFF),
                    onSurface = Color(0xFFE7E6D5),
                    onSurface2 = Color(0xFFC6C5B3),
                    onSurfaceDim = Color(0xFF9C9D8C),
                    onSurfaceFaint = Color(0xFF6B6C5D),
                    accent = generated[0],
                    accentPress = generated[1],
                    accentSoft = generated[2],
                    accentSoft2 = generated[3],
                    onAccent = Color(0xFF00210A),
                    onAccentSoft = generated[4],
                    warn = Color(0xFFEBA85A),
                    warnSoft = Color(0xFF4B361A),
                    info = Color(0xFF9DB3F0),
                    infoSoft = Color(0xFF2A325A),
                    danger = Color(0xFFE9837A),
                )
            } else {
                val generated = getDynamicLightAccentColors(baseColor)
                EdgeXColors(
                    bg = Color(0xFFF2EEE5),
                    surface = Color.White,
                    surface1 = Color(0xFFF7F3EA),
                    surface2 = Color(0xFFECE6D6),
                    surface3 = Color(0xFFE1D9C5),
                    surfaceTint = Color(0xFFD7CEB6),
                    outline = Color(0x1A1B1E14),
                    outlineStrong = Color(0x381B1E14),
                    onSurface = Color(0xFF15180F),
                    onSurface2 = Color(0xFF3A3D31),
                    onSurfaceDim = Color(0xFF6B6E5F),
                    onSurfaceFaint = Color(0xFF989B8B),
                    accent = generated[0],
                    accentPress = generated[1],
                    accentSoft = generated[2],
                    accentSoft2 = generated[3],
                    onAccent = Color.White,
                    onAccentSoft = generated[4],
                    warn = Color(0xFFC77A1F),
                    warnSoft = Color(0xFFFDE9C9),
                    info = Color(0xFF4B6BCC),
                    infoSoft = Color(0xFFDEE5FA),
                    danger = Color(0xFFB23B3B),
                )
            }
        } else {
            if (darkTheme) darkEdgeXColors(accent) else lightEdgeXColors(accent)
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalEdgeXColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(darkTheme),
            typography = edgeXTypography(),
            shapes = edgeXShapes(),
            content = content,
        )
    }
}

private fun getDynamicLightAccentColors(baseColor: Color): List<Color> {
    val argb = baseColor.toArgb()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]
    
    val lightAccent = baseColor
    
    val pressColor = ColorUtils.HSLToColor(floatArrayOf(h, s, (l * 0.8f).coerceIn(0f, 1f)))
    val lightAccentPress = Color(pressColor)
    
    val softColor = ColorUtils.HSLToColor(floatArrayOf(h, (s * 0.5f).coerceIn(0f, 1f), 0.88f))
    val lightAccentSoft = Color(softColor)
    
    val soft2Color = ColorUtils.HSLToColor(floatArrayOf(h, (s * 0.3f).coerceIn(0f, 1f), 0.94f))
    val lightAccentSoft2 = Color(soft2Color)
    
    val onSoftColor = ColorUtils.HSLToColor(floatArrayOf(h, s, 0.14f))
    val lightOnAccentSoft = Color(onSoftColor)
    
    return listOf(lightAccent, lightAccentPress, lightAccentSoft, lightAccentSoft2, lightOnAccentSoft)
}

private fun getDynamicDarkAccentColors(baseColor: Color): List<Color> {
    val argb = baseColor.toArgb()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]
    
    val darkL = if (l < 0.65f) 0.68f else l
    val darkS = if (s > 0.8f) s * 0.85f else s
    
    val darkAccent = Color(ColorUtils.HSLToColor(floatArrayOf(h, darkS, darkL)))
    
    val pressColor = ColorUtils.HSLToColor(floatArrayOf(h, darkS, (darkL * 0.85f).coerceIn(0f, 1f)))
    val darkAccentPress = Color(pressColor)
    
    val softColor = ColorUtils.HSLToColor(floatArrayOf(h, darkS, 0.20f))
    val darkAccentSoft = Color(softColor)
    
    val soft2Color = ColorUtils.HSLToColor(floatArrayOf(h, darkS, 0.12f))
    val darkAccentSoft2 = Color(soft2Color)
    
    val onSoftColor = ColorUtils.HSLToColor(floatArrayOf(h, darkS, 0.85f))
    val darkOnAccentSoft = Color(onSoftColor)
    
    return listOf(darkAccent, darkAccentPress, darkAccentSoft, darkAccentSoft2, darkOnAccentSoft)
}

private fun lightEdgeXColors(accent: EdgeXAccent) = EdgeXColors(
    bg = Color(0xFFF2EEE5),
    surface = Color.White,
    surface1 = Color(0xFFF7F3EA),
    surface2 = Color(0xFFECE6D6),
    surface3 = Color(0xFFE1D9C5),
    surfaceTint = Color(0xFFD7CEB6),
    outline = Color(0x1A1B1E14),
    outlineStrong = Color(0x381B1E14),
    onSurface = Color(0xFF15180F),
    onSurface2 = Color(0xFF3A3D31),
    onSurfaceDim = Color(0xFF6B6E5F),
    onSurfaceFaint = Color(0xFF989B8B),
    accent = accent.lightAccent,
    accentPress = accent.lightAccentPress,
    accentSoft = accent.lightAccentSoft,
    accentSoft2 = accent.lightAccentSoft2,
    onAccent = Color.White,
    onAccentSoft = accent.lightOnAccentSoft,
    warn = Color(0xFFC77A1F),
    warnSoft = Color(0xFFFDE9C9),
    info = Color(0xFF4B6BCC),
    infoSoft = Color(0xFFDEE5FA),
    danger = Color(0xFFB23B3B),
)

private fun darkEdgeXColors(accent: EdgeXAccent) = EdgeXColors(
    bg = Color(0xFF0E110A),
    surface = Color(0xFF181C12),
    surface1 = Color(0xFF1F2418),
    surface2 = Color(0xFF272D1F),
    surface3 = Color(0xFF313826),
    surfaceTint = Color(0xFF3D4630),
    outline = Color(0x14FFFFFF),
    outlineStrong = Color(0x2EFFFFFF),
    onSurface = Color(0xFFE7E6D5),
    onSurface2 = Color(0xFFC6C5B3),
    onSurfaceDim = Color(0xFF9C9D8C),
    onSurfaceFaint = Color(0xFF6B6C5D),
    accent = accent.darkAccent,
    accentPress = accent.darkAccentPress,
    accentSoft = accent.darkAccentSoft,
    accentSoft2 = accent.darkAccentSoft2,
    onAccent = Color(0xFF00210A),
    onAccentSoft = accent.darkOnAccentSoft,
    warn = Color(0xFFEBA85A),
    warnSoft = Color(0xFF4B361A),
    info = Color(0xFF9DB3F0),
    infoSoft = Color(0xFF2A325A),
    danger = Color(0xFFE9837A),
)

private fun EdgeXColors.toMaterialColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            secondary = accentSoft,
            onSecondary = onAccentSoft,
            background = bg,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surface2,
            onSurfaceVariant = onSurface2,
            outline = outlineStrong,
            error = danger,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            secondary = accentSoft,
            onSecondary = onAccentSoft,
            background = bg,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surface2,
            onSurfaceVariant = onSurface2,
            outline = outlineStrong,
            error = danger,
        )
    }

private fun edgeXTypography() = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 40.sp,
    ),
    headlineLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 31.sp,
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private fun edgeXShapes() = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(EdgeXRadius.xs),
    small = androidx.compose.foundation.shape.RoundedCornerShape(EdgeXRadius.sm),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(EdgeXRadius.md),
    large = androidx.compose.foundation.shape.RoundedCornerShape(EdgeXRadius.lg),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(EdgeXRadius.xl),
)
