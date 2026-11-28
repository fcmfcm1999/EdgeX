package com.fan.edgex.ui.compose.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.FreezerAppPolicy
import com.fan.edgex.config.getConfigString
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isFrozen: Boolean = false,
)

private val frozenIconColorFilter = ColorMatrixColorFilter(
    ColorMatrix().apply { setSaturation(0f) },
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
        if (open) {
            apps = withContext(Dispatchers.IO) {
                context.loadLaunchableApps(includeFrozenFreezerApps = true)
            }
        } else {
            query = ""
        }
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
                        if (app.isFrozen) {
                            imageView.colorFilter = frozenIconColorFilter
                            imageView.alpha = 0.55f
                        } else {
                            imageView.colorFilter = null
                            imageView.alpha = 1f
                        }
                    },
                    modifier = Modifier.size(30.dp),
                )
            } else {
                EdgeXIconBox(EdgeXIcons.LaunchApp, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.isFrozen) {
                    Text(
                        text = stringResource(R.string.compose_app_frozen),
                        color = colors.info,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.infoSoft)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
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

internal fun Context.loadLaunchableApps(includeFrozenFreezerApps: Boolean = false): List<AppItem> {
    val pm = packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val enabledApps = pm.queryIntentActivities(intent, 0)
        .map { info ->
            AppItem(
                packageName = info.activityInfo.packageName,
                label = info.loadLabel(pm).toString(),
                icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                isFrozen = false,
            )
        }

    val freezerPackages = if (includeFrozenFreezerApps) {
        FreezerAppPolicy.parseFreezerPackages(getConfigString(AppConfig.FREEZER_APP_LIST))
    } else {
        emptySet()
    }

    val disabledCandidates = if (freezerPackages.isNotEmpty()) {
        pm.queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
            .filter { info ->
                val appInfo = info.activityInfo.applicationInfo
                !appInfo.enabled && appInfo.packageName in freezerPackages
            }
            .map { info ->
                AppItem(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                    isFrozen = true,
                )
            }
    } else {
        emptyList()
    }

    return FreezerAppPolicy.mergeLaunchableApps(
        enabledApps = enabledApps,
        disabledCandidates = disabledCandidates,
        freezerPackages = freezerPackages,
        packageNameOf = { it.packageName },
        enabledOf = { !it.isFrozen },
        labelOf = { it.label },
    )
}
