package com.fan.edgex.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.R
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

@Composable
fun EdgeXTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalEdgeXColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            EdgeXIconButton(onClick = onBack) {
                EdgeXIcon(EdgeXIcons.Back, contentDescription = stringResource(R.string.compose_back), tint = colors.onSurface)
            }
        }
        Text(
            text = title,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
fun EdgeXIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (tonal) colors.accentSoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun EdgeXIconBox(
    imageVector: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    background: Color = LocalEdgeXColors.current.accentSoft,
    tint: Color = LocalEdgeXColors.current.onAccentSoft,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(EdgeXRadius.sm))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        EdgeXIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun EdgeXTile(
    title: String,
    meta: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconBackground: Color = LocalEdgeXColors.current.accentSoft,
    iconTint: Color = LocalEdgeXColors.current.onAccentSoft,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(EdgeXRadius.lg),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EdgeXIconBox(
                    imageVector = icon,
                    contentDescription = null,
                    background = iconBackground,
                    tint = iconTint,
                    modifier = Modifier.size(40.dp),
                )
                Column {
                    Text(title, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(meta, color = colors.onSurfaceDim, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun EdgeXListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EdgeXRadius.lg))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(EdgeXRadius.lg)),
        content = content,
    )
}

@Composable
fun EdgeXRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Int? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalEdgeXColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            EdgeXIconBox(imageVector = icon, contentDescription = null)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = colors.onSurfaceDim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
fun EdgeXSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Int? = null,
) {
    EdgeXRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
    ) {
        EdgeXSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun EdgeXSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAccent,
            checkedTrackColor = colors.accent,
            checkedBorderColor = colors.accent,
            uncheckedThumbColor = colors.onSurfaceDim,
            uncheckedTrackColor = colors.surface2,
            uncheckedBorderColor = colors.outlineStrong,
        ),
    )
}

@Composable
fun <T> EdgeXSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            EdgeXChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelected(option) },
            )
        }
    }
}

@Composable
fun EdgeXChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    TextButton(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) colors.accentSoft else colors.surface1,
            contentColor = if (selected) colors.onAccentSoft else colors.onSurface2,
        ),
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeXBottomSheet(
    open: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalEdgeXColors.current
    if (open) {
        val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = skipPartiallyExpanded
        )
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            containerColor = colors.surface,
            contentColor = colors.onSurface,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.outlineStrong),
                )
            },
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                Text(
                    text = title,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                )
                content()
            }
        }
    }
}


@Composable
fun EdgeXToast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = !message.isNullOrBlank()) {
            Surface(
                color = colors.onSurface,
                contentColor = colors.surface,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = message.orEmpty(),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun EdgeXDivider() {
    val colors = LocalEdgeXColors.current
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.outline),
    )
}

@Composable
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )

@Composable
fun PreviewSectionHeader(title: String, subtitle: String) {
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 6.dp)) {
        Text(title, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(subtitle, color = colors.onSurfaceDim, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
fun PhoneFrame(
    modifier: Modifier = Modifier,
    width: Dp = 176.dp,
    height: Dp = 320.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF1D2018))
            .border(1.dp, colors.accent.copy(alpha = 0.24f), RoundedCornerShape(30.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 11.dp)
                .width(24.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.35f)),
        )
        content()
    }
}
