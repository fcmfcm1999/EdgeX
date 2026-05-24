package com.fan.edgex.ui.compose.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object EdgeXIcons {
    val Back = strokeIcon("back") { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f) }
    val Search = strokeIcon("search") {
        moveTo(19f, 19f); lineTo(15.5f, 15.5f)
        moveTo(11f, 18f)
        arcToRelative(7f, 7f, 0f, true, true, 0f, -14f)
        arcToRelative(7f, 7f, 0f, false, true, 0f, 14f)
    }
    val More = strokeIcon("more", strokeWidth = 3f) {
        moveTo(12f, 6f); lineTo(12.01f, 6f)
        moveTo(12f, 12f); lineTo(12.01f, 12f)
        moveTo(12f, 18f); lineTo(12.01f, 18f)
    }
    val ChevronRight = strokeIcon("chevron_right") { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
    val Check = strokeIcon("check", strokeWidth = 2.5f) { moveTo(5f, 13f); lineTo(9f, 17f); lineTo(19f, 7f) }
    val Plus = strokeIcon("plus", strokeWidth = 2.5f) { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    val Gesture = strokeIcon("gesture") {
        moveTo(5f, 12f)
        curveTo(5f, 9f, 7f, 5f, 12f, 5f)
        curveTo(16f, 5f, 18f, 8f, 18f, 11f)
        curveTo(18f, 15f, 15f, 17f, 11f, 17f)
        curveTo(9f, 17f, 8f, 18f, 8f, 20f)
        lineTo(8f, 23f)
    }
    val Freeze = strokeIcon("freeze", strokeWidth = 1.7f) {
        moveTo(12f, 3f); lineTo(12f, 21f)
        moveTo(3f, 12f); lineTo(21f, 12f)
        moveTo(5.5f, 5.5f); lineTo(18.5f, 18.5f)
        moveTo(18.5f, 5.5f); lineTo(5.5f, 18.5f)
    }
    val Keys = strokeIcon("keys") {
        moveTo(11f, 11f)
        arcToRelative(4f, 4f, 0f, true, true, -4f, 4f)
        lineTo(3.5f, 18.5f); lineTo(5f, 20f); lineTo(6f, 19f); lineTo(7f, 20f); lineTo(9f, 18f)
    }
    val Pie = fillIcon("pie") {
        moveTo(12f, 2f); lineTo(12f, 12f); lineTo(20.7f, 17f)
        arcTo(10f, 10f, 0f, true, true, 12f, 2f)
        close()
    }
    val Multi = strokeIcon("multi", strokeWidth = 2.2f) {
        moveTo(4f, 6f); lineTo(14f, 6f)
        moveTo(4f, 12f); lineTo(20f, 12f)
        moveTo(4f, 18f); lineTo(11f, 18f)
    }
    val Theme = fillIcon("theme") {
        moveTo(12f, 3f)
        arcToRelative(9f, 9f, 0f, true, false, 0f, 18f)
        curveToRelative(0.6f, 0f, 1f, -0.5f, 1f, -1f)
        curveToRelative(0f, -0.4f, -0.3f, -0.7f, -0.6f, -0.9f)
        curveToRelative(-0.3f, -0.2f, -0.4f, -0.4f, -0.4f, -0.7f)
        curveToRelative(0f, -0.6f, 0.4f, -1f, 1f, -1f)
        horizontalLineToRelative(1.6f)
        curveToRelative(2.4f, 0f, 4.4f, -2f, 4.4f, -4.4f)
        curveTo(19f, 8.6f, 15.9f, 3f, 12f, 3f)
        close()
    }
    val Sparkle = strokeIcon("sparkle", strokeWidth = 1.6f) {
        moveTo(12f, 3f); lineTo(12f, 9f)
        moveTo(12f, 15f); lineTo(12f, 21f)
        moveTo(3f, 12f); lineTo(9f, 12f)
        moveTo(15f, 12f); lineTo(21f, 12f)
        moveTo(5f, 5f); lineTo(9f, 9f)
        moveTo(15f, 15f); lineTo(19f, 19f)
        moveTo(19f, 5f); lineTo(15f, 9f)
        moveTo(9f, 15f); lineTo(5f, 19f)
    }
}

@Composable
fun EdgeXIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

private fun strokeIcon(
    name: String,
    strokeWidth: Float = 2f,
    builder: PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = builder,
        )
    }
    .build()

private fun fillIcon(
    name: String,
    builder: PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathFillType = PathFillType.NonZero,
            pathBuilder = builder,
        )
    }
    .build()
