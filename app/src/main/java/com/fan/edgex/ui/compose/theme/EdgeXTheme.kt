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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    Green(
        id = "green",
        lightAccent = Color(0xFF2F8A3E),
        lightAccentPress = Color(0xFF237030),
        lightAccentSoft = Color(0xFFC7EFCC),
        lightAccentSoft2 = Color(0xFFDCF4DE),
        lightOnAccentSoft = Color(0xFF0B3D14),
        darkAccent = Color(0xFF8AD995),
        darkAccentPress = Color(0xFF6FC97D),
        darkAccentSoft = Color(0xFF1F4A26),
        darkAccentSoft2 = Color(0xFF143019),
        darkOnAccentSoft = Color(0xFFBDF0C5),
    ),
    Blue(
        id = "blue",
        lightAccent = Color(0xFF3B6CE5),
        lightAccentPress = Color(0xFF2B58C9),
        lightAccentSoft = Color(0xFFD5E0FB),
        lightAccentSoft2 = Color(0xFFE7EDFC),
        lightOnAccentSoft = Color(0xFF0C2774),
        darkAccent = Color(0xFFA8C2F6),
        darkAccentPress = Color(0xFF86A8F0),
        darkAccentSoft = Color(0xFF1B2A55),
        darkAccentSoft2 = Color(0xFF0F1B3B),
        darkOnAccentSoft = Color(0xFFD5E0FB),
    ),
    Coral(
        id = "coral",
        lightAccent = Color(0xFFDD5A48),
        lightAccentPress = Color(0xFFC04432),
        lightAccentSoft = Color(0xFFFBD7CF),
        lightAccentSoft2 = Color(0xFFFCE8E3),
        lightOnAccentSoft = Color(0xFF5B1206),
        darkAccent = Color(0xFFF0978A),
        darkAccentPress = Color(0xFFE67F70),
        darkAccentSoft = Color(0xFF5B1206),
        darkAccentSoft2 = Color(0xFF3B0A04),
        darkOnAccentSoft = Color(0xFFFBD7CF),
    ),
    Violet(
        id = "violet",
        lightAccent = Color(0xFF7B4FE0),
        lightAccentPress = Color(0xFF6238C8),
        lightAccentSoft = Color(0xFFE1D5FA),
        lightAccentSoft2 = Color(0xFFEDE5FC),
        lightOnAccentSoft = Color(0xFF260A6B),
        darkAccent = Color(0xFFC5AEF6),
        darkAccentPress = Color(0xFFAD8EF0),
        darkAccentSoft = Color(0xFF321B6F),
        darkAccentSoft2 = Color(0xFF1F1148),
        darkOnAccentSoft = Color(0xFFE1D5FA),
    ),
    Amber(
        id = "amber",
        lightAccent = Color(0xFFC68A1A),
        lightAccentPress = Color(0xFFA87311),
        lightAccentSoft = Color(0xFFF6E2B4),
        lightAccentSoft2 = Color(0xFFFBEDC9),
        lightOnAccentSoft = Color(0xFF3B2402),
        darkAccent = Color(0xFFEFC56D),
        darkAccentPress = Color(0xFFDFAE48),
        darkAccentSoft = Color(0xFF4A3408),
        darkAccentSoft2 = Color(0xFF2D1F04),
        darkOnAccentSoft = Color(0xFFF6E2B4),
    );

    companion object {
        fun fromId(id: String?): EdgeXAccent =
            entries.firstOrNull { it.id == id } ?: Green
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

val LocalEdgeXColors = staticCompositionLocalOf { lightEdgeXColors(EdgeXAccent.Green) }

@Composable
fun EdgeXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: EdgeXAccent = EdgeXAccent.Green,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) darkEdgeXColors(accent) else lightEdgeXColors(accent)
    androidx.compose.runtime.CompositionLocalProvider(LocalEdgeXColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(darkTheme),
            typography = edgeXTypography(),
            shapes = edgeXShapes(),
            content = content,
        )
    }
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
