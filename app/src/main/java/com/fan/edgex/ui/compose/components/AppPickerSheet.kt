package com.fan.edgex.ui.compose.components

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fan.edgex.R
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

@Composable
fun AppPickerSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onPick: (AppItem) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(open) {
        if (open && apps.isEmpty()) {
            apps = withContext(Dispatchers.IO) { context.loadLaunchableApps() }
        }
        if (!open) query = ""
    }
    EdgeXBottomSheet(open = open, title = stringResource(R.string.action_launch_app), onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = { Text(stringResource(R.string.hint_search_apps), color = colors.onSurfaceDim) },
            singleLine = true,
            shape = RoundedCornerShape(EdgeXRadius.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.accent,
            ),
        )
        val filtered = remember(apps, query) {
            val q = query.trim()
            if (q.isBlank()) {
                apps
            } else {
                apps.filter {
                    it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            EdgeXListGroup {
                filtered.forEachIndexed { index, app ->
                    AppRow(app = app, onClick = { onPick(app) })
                    if (index != filtered.lastIndex) EdgeXDivider()
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppItem, onClick: () -> Unit) {
    val colors = LocalEdgeXColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (app.icon != null) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                    },
                    update = { imageView ->
                        val drawable = app.icon.constantState?.newDrawable()?.mutate() ?: app.icon
                        imageView.setImageDrawable(drawable)
                    },
                    modifier = Modifier.size(30.dp),
                )
            } else {
                EdgeXIconBox(EdgeXIcons.LaunchApp, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                color = colors.onSurfaceDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Context.loadLaunchableApps(): List<AppItem> {
    val pm = packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { info ->
            AppItem(
                packageName = info.activityInfo.packageName,
                label = info.loadLabel(pm).toString(),
                icon = runCatching { info.loadIcon(pm) }.getOrNull(),
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}
