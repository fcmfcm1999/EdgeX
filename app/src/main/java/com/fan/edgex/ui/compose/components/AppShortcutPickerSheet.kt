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

data class ShortcutItem(
    val packageName: String,
    val shortcutId: String,
    val label: String,
    val appLabel: String,
    val icon: Drawable?,
)

@Composable
fun AppShortcutPickerSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onPick: (ShortcutItem) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    var shortcuts by remember { mutableStateOf(emptyList<ShortcutItem>()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(open) {
        if (open && shortcuts.isEmpty()) {
            shortcuts = withContext(Dispatchers.IO) { context.loadShortcuts() }
        }
        if (!open) query = ""
    }

    EdgeXBottomSheet(open = open, title = stringResource(R.string.action_app_shortcut), onDismissRequest = onDismiss) {
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
        val filtered = remember(shortcuts, query) {
            val q = query.trim()
            if (q.isBlank()) {
                shortcuts
            } else {
                shortcuts.filter {
                    it.label.contains(q, ignoreCase = true) ||
                        it.appLabel.contains(q, ignoreCase = true) ||
                        it.packageName.contains(q, ignoreCase = true)
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
                filtered.forEachIndexed { index, shortcut ->
                    ShortcutRow(shortcut = shortcut, onClick = { onPick(shortcut) })
                    if (index != filtered.lastIndex) EdgeXDivider()
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(shortcut: ShortcutItem, onClick: () -> Unit) {
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
            if (shortcut.icon != null) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                    },
                    update = { imageView ->
                        val drawable = shortcut.icon.constantState?.newDrawable()?.mutate() ?: shortcut.icon
                        imageView.setImageDrawable(drawable)
                    },
                    modifier = Modifier.size(30.dp),
                )
            } else {
                EdgeXIconBox(EdgeXIcons.AppShortcut, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shortcut.label,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = shortcut.appLabel,
                color = colors.onSurfaceDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Context.loadShortcuts(): List<ShortcutItem> {
    val result = loadShortcutsViaLauncherApps()
    return result.ifEmpty { loadShortcutsViaRoot() }
}

private fun Context.loadShortcutsViaLauncherApps(): List<ShortcutItem> {
    val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
    val pm = packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = pm.queryIntentActivities(mainIntent, 0)
    val tempList = mutableListOf<ShortcutItem>()
    for (app in apps) {
        val packageName = app.activityInfo.packageName
        val appLabel = app.loadLabel(pm).toString()
        try {
            val query = android.content.pm.LauncherApps.ShortcutQuery()
            query.setQueryFlags(
                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
            query.setPackage(packageName)
            val appShortcuts = launcherApps.getShortcuts(query, android.os.Process.myUserHandle()) ?: emptyList()
            for (shortcut in appShortcuts) {
                val icon = try {
                    launcherApps.getShortcutIconDrawable(shortcut, 0)
                } catch (e: Exception) {
                    app.loadIcon(pm)
                }
                tempList.add(
                    ShortcutItem(
                        packageName = packageName,
                        shortcutId = shortcut.id,
                        label = shortcut.shortLabel?.toString() ?: shortcut.longLabel?.toString() ?: "",
                        appLabel = appLabel,
                        icon = icon,
                    )
                )
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }
    return tempList.sortedWith(compareBy({ it.appLabel }, { it.label }))
}

private fun Context.loadShortcutsViaRoot(): List<ShortcutItem> {
    val rootShortcuts = mutableListOf<ShortcutItem>()
    try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys shortcut"))
        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
        var line: String?
        var currentPackage: String? = null
        var currentId: String? = null
        var currentLabel: String? = null
        val pm = packageManager
        while (reader.readLine().also { line = it } != null) {
            val l = line!!.trim()
            if (l.startsWith("Package:") && l.contains("uid=")) {
                val parts = l.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    currentPackage = parts[1]
                }
            }
            if (l.startsWith("ShortcutInfo") && l.contains("id=")) {
                val afterId = l.substringAfter("id=")
                currentId = afterId.substringBefore(",").substringBefore(" ").trim()
                currentLabel = null
            } else if (l.startsWith("id=")) {
                currentId = l.substringAfter("id=").trim()
            }
            if (l.startsWith("packageName=")) {
                currentPackage = l.substringAfter("packageName=").trim()
            }
            if (l.startsWith("shortLabel=")) {
                val raw = l.substringAfter("shortLabel=")
                currentLabel = if (raw.contains(", resId=")) {
                    raw.substringBefore(", resId=")
                } else {
                    raw.substringBefore(",")
                }
                currentLabel = currentLabel?.trim()
                if (currentPackage != null && currentId != null && currentLabel != null) {
                    val exists = rootShortcuts.any { it.packageName == currentPackage && it.shortcutId == currentId }
                    if (!exists) {
                        try {
                            val appInfo = pm.getApplicationInfo(currentPackage!!, 0)
                            rootShortcuts.add(
                                ShortcutItem(
                                    packageName = currentPackage!!,
                                    shortcutId = currentId!!,
                                    label = currentLabel!!,
                                    appLabel = appInfo.loadLabel(pm).toString(),
                                    icon = appInfo.loadIcon(pm),
                                )
                            )
                        } catch (_: Exception) {
                        }
                    }
                    currentId = null
                    currentLabel = null
                }
            }
        }
        reader.close()
        process.waitFor()
    } catch (_: Exception) {
    }
    return rootShortcuts.sortedWith(compareBy({ it.appLabel }, { it.label }))
}
