package com.fan.edgex.ui.compose.components

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fan.edgex.R
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

@Composable
fun LaunchActivitySheet(
    open: Boolean,
    prefKey: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    var target by remember { mutableStateOf("") }

    LaunchedEffect(open) {
        if (open) {
            val existing = context.getConfigString(prefKey)
            target = if (existing.startsWith("launch_activity:")) {
                existing.removePrefix("launch_activity:")
            } else {
                ""
            }
        }
    }

    EdgeXBottomSheet(
        open = open,
        title = stringResource(R.string.action_launch_activity),
        onDismissRequest = onDismiss,
    ) {
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = {
                Text(
                    stringResource(R.string.hint_launch_activity),
                    color = colors.onSurfaceDim,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(EdgeXRadius.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.accent,
            ),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val trimmed = target.trim()
                if (trimmed.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_launch_activity_empty),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@Button
                }
                val parts = trimmed.split("/")
                if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_launch_activity_invalid),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@Button
                }
                context.putConfigsSync(
                    prefKey to "launch_activity:$trimmed",
                    "${prefKey}_label" to trimmed,
                    "${prefKey}_title" to parts[1].substringAfterLast('.'),
                )
                onSave()
            },
            modifier = Modifier.fillMaxWidth(),
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
