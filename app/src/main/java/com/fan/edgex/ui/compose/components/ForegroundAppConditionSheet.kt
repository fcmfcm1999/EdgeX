package com.fan.edgex.ui.compose.components

import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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

@Composable
fun ForegroundAppConditionSheet(
    open: Boolean,
    initialPackageNames: Set<String>,
    onDismiss: () -> Unit,
    onSave: (packageNames: Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    var query by remember(open) { mutableStateOf("") }
    var selectedPackages by remember(open, initialPackageNames) {
        mutableStateOf(initialPackageNames)
    }

    LaunchedEffect(open) {
        if (open && apps.isEmpty()) {
            apps = withContext(Dispatchers.IO) { context.loadLaunchableApps() }
        }
    }

    EdgeXBottomSheet(
        open = open,
        title = stringResource(R.string.cond_foreground_app),
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("foreground_app_condition_sheet"),
    ) {
        Text(
            text = stringResource(R.string.cond_foreground_selected_count, selectedPackages.size),
            color = colors.onSurfaceDim,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(vertical = 10.dp)
                .testTag("foreground_app_selected_count"),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("foreground_app_search"),
            placeholder = { Text(stringResource(R.string.hint_search_apps), color = colors.onSurfaceDim) },
            singleLine = true,
            shape = RoundedCornerShape(EdgeXRadius.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.accent,
            ),
        )

        val filteredApps = remember(apps, query) {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isEmpty()) {
                apps
            } else {
                apps.filter { app ->
                    app.label.contains(normalizedQuery, ignoreCase = true) ||
                        app.packageName.contains(normalizedQuery, ignoreCase = true)
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
                filteredApps.forEachIndexed { index, app ->
                    ForegroundAppRow(
                        app = app,
                        checked = app.packageName in selectedPackages,
                        onToggle = {
                            selectedPackages = if (app.packageName in selectedPackages) {
                                selectedPackages - app.packageName
                            } else {
                                selectedPackages + app.packageName
                            }
                        },
                    )
                    if (index != filteredApps.lastIndex) EdgeXDivider()
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("foreground_app_cancel"),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = { onSave(selectedPackages) },
                enabled = selectedPackages.isNotEmpty(),
                modifier = Modifier.testTag("foreground_app_save"),
                shape = RoundedCornerShape(EdgeXRadius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text(stringResource(R.string.btn_save))
            }
        }
    }
}

@Composable
private fun ForegroundAppRow(
    app: AppItem,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("foreground_app_package_${app.packageName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (app.icon != null) {
                AndroidView(
                    factory = { imageContext ->
                        ImageView(imageContext).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
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
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.testTag("foreground_app_checkbox_${app.packageName}"),
        )
    }
}
