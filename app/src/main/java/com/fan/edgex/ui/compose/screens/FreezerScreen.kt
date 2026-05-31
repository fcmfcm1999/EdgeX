package com.fan.edgex.ui.compose.screens

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.FreezerBootstrap
import com.fan.edgex.config.configPrefs
import com.fan.edgex.config.putConfig
import com.fan.edgex.ui.compose.components.EdgeXChip
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXSwitch
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.absoluteValue

private enum class FreezerFilter {
    All,
    Frozen,
    Active,
}

private data class FreezerApp(
    val packageName: String,
    val label: String,
    val frozen: Boolean,
    val icon: Drawable?,
) {
    val avatar: String = label.trim().take(1).ifBlank { packageName.take(1).uppercase() }
}

@Composable
fun FreezerScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<FreezerApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FreezerFilter.All) }
    var busyPackage by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            apps = context.loadFreezerApps()
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        FreezerBootstrap.ensureMigrated(context)
        apps = context.loadFreezerApps()
        loading = false
    }

    val displayed = apps.filter { app ->
        val matchesQuery = query.isBlank()
            || app.label.contains(query, ignoreCase = true)
            || app.packageName.contains(query, ignoreCase = true)
        val matchesFilter = when (filter) {
            FreezerFilter.All -> true
            FreezerFilter.Frozen -> app.frozen
            FreezerFilter.Active -> !app.frozen
        }
        matchesQuery && matchesFilter
    }
    val frozenCount = apps.count { it.frozen }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            item {
                EdgeXTopBar(title = stringResource(R.string.header_freezer), onBack = onBack)
                FreezerHeader(frozenCount = frozenCount, total = apps.size)
                SearchBox(query = query, onQueryChange = { query = it })
                FilterTabs(
                    filter = filter,
                    onFilter = { filter = it },
                )
            }
            if (loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = LocalEdgeXColors.current.accent)
                    }
                }
            } else {
                item {
                    EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        displayed.forEachIndexed { index, app ->
                            FreezerAppRow(
                                app = app,
                                busy = busyPackage == app.packageName,
                                onFreezeChange = { freeze ->
                                    busyPackage = app.packageName
                                    scope.launch {
                                        val success = withContext(Dispatchers.IO) {
                                            runRootCommand(if (freeze) "pm disable ${app.packageName}" else "pm enable ${app.packageName}")
                                        }
                                        if (success) {
                                            context.updateFreezerList(app.packageName, freeze)
                                            showToast(context.getString(if (freeze) R.string.compose_freeze_success else R.string.compose_unfreeze_success, app.label))
                                            reload()
                                        } else {
                                            showToast(context.getString(if (freeze) R.string.compose_freeze_failed else R.string.compose_unfreeze_failed))
                                        }
                                        busyPackage = null
                                    }
                                },
                            )
                            if (index != displayed.lastIndex) EdgeXDivider()
                        }
                        if (displayed.isEmpty()) {
                            EmptyFreezerRow()
                        }
                    }
                }
            }
        }

        if (frozenCount > 0) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        showToast(context.getString(R.string.compose_refreezing))
                        withContext(Dispatchers.IO) {
                            apps.filter { it.frozen }.forEach {
                                runRootCommand("pm disable ${it.packageName}")
                            }
                        }
                        reload()
                        showToast(context.getString(R.string.compose_refrozen))
                    }
                },
                containerColor = LocalEdgeXColors.current.accent,
                contentColor = LocalEdgeXColors.current.onAccent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(22.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EdgeXIcon(EdgeXIcons.Refreeze, contentDescription = null)
                    Text(stringResource(R.string.compose_refreeze_all), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FreezerHeader(frozenCount: Int, total: Int) {
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$frozenCount",
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 34.sp,
            )
            Text(
                text = " / $total",
                color = colors.onSurfaceDim,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 32.sp,
            )
        }
        Text(
            text = stringResource(R.string.compose_freezer_saving, String.format(Locale.getDefault(), "%.1f", frozenCount * 0.16)),
            color = colors.onSurfaceDim,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    val colors = LocalEdgeXColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 14.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.surface1)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EdgeXIcon(EdgeXIcons.Search, contentDescription = null, tint = colors.onSurfaceDim, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
            modifier = Modifier
                .testTag("freezer_search")
                .weight(1f),
            decorationBox = { innerTextField ->
                if (query.isBlank()) {
                    Text(stringResource(R.string.compose_search_apps_hint), color = colors.onSurfaceDim, fontSize = 15.sp)
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun FilterTabs(
    filter: FreezerFilter,
    onFilter: (FreezerFilter) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FreezerFilter.entries.forEach {
            EdgeXChip(
                label = stringResource(freezerFilterLabel(it)),
                selected = it == filter,
                onClick = { onFilter(it) },
            )
        }
    }
}

private fun freezerFilterLabel(filter: FreezerFilter): Int =
    when (filter) {
        FreezerFilter.All -> R.string.compose_filter_all
        FreezerFilter.Frozen -> R.string.compose_app_frozen
        FreezerFilter.Active -> R.string.compose_filter_active
    }

@Composable
private fun FreezerAppRow(
    app: FreezerApp,
    busy: Boolean,
    onFreezeChange: (Boolean) -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppAvatar(app)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.frozen) {
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
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = colors.accent, strokeWidth = 3.dp)
        } else {
            EdgeXSwitch(checked = app.frozen, onCheckedChange = onFreezeChange)
        }
    }
}

@Composable
private fun AppAvatar(app: FreezerApp) {
    val bitmap = remember(app.packageName, app.icon) {
        runCatching { app.icon?.toBitmap(width = 96, height = 96)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(EdgeXRadius.sm)),
        )
        return
    }

    val colors = avatarColors(app.packageName)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(EdgeXRadius.sm))
            .background(colors.first),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = app.avatar,
            color = colors.second,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
    }
}

@Composable
private fun EmptyFreezerRow() {
    val colors = LocalEdgeXColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.compose_no_matching_apps), color = colors.onSurfaceDim, fontWeight = FontWeight.Medium)
    }
}

private suspend fun Context.loadFreezerApps(): List<FreezerApp> = withContext(Dispatchers.IO) {
    val pm = packageManager
    val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())
    pm.getInstalledApplications(flags)
        .filter { it.packageName != packageName }
        .map { info ->
            FreezerApp(
                packageName = info.packageName,
                label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.packageName),
                frozen = !info.enabled,
                icon = runCatching { info.loadIcon(pm) }.getOrNull(),
            )
        }
        .sortedWith(compareBy<FreezerApp> { !it.frozen }.thenBy { it.label.lowercase(Locale.getDefault()) })
}

private fun Context.updateFreezerList(packageName: String, freeze: Boolean) {
    val current = configPrefs()
        .getString(AppConfig.FREEZER_APP_LIST, "")
        .orEmpty()
        .split(',')
        .filter { it.isNotBlank() }
        .toMutableSet()
    if (freeze) {
        current += packageName
    } else {
        current -= packageName
    }
    putConfig(AppConfig.FREEZER_APP_LIST, current.joinToString(","))
}

private fun runRootCommand(command: String): Boolean =
    runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        process.waitFor() == 0
    }.getOrDefault(false)

private fun avatarColors(seed: String): Pair<Color, Color> {
    val palette = listOf(
        Color(0xFFFF8A00),
        Color(0xFF3A92B8),
        Color(0xFFE86D58),
        Color(0xFF37A04B),
        Color(0xFF7A6241),
        Color(0xFF23AAA5),
        Color(0xFF6977D9),
    )
    return palette[seed.hashCode().absoluteValue % palette.size] to Color.White
}
