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
fun ShellCommandSheet(
    open: Boolean,
    prefKey: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    var command by remember { mutableStateOf("") }
    var runAsRoot by remember { mutableStateOf(false) }

    LaunchedEffect(open) {
        if (open) {
            val existing = context.getConfigString(prefKey)
            if (existing.startsWith("shell:")) {
                val parts = existing.removePrefix("shell:").split(":", limit = 2)
                if (parts.size == 2) {
                    runAsRoot = parts[0] == "true"
                    command = parts[1]
                }
            } else {
                command = ""
                runAsRoot = false
            }
        }
    }

    EdgeXBottomSheet(
        open = open,
        title = stringResource(R.string.action_shell_command),
        onDismissRequest = onDismiss,
    ) {
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = { Text(stringResource(R.string.hint_shell_command), color = colors.onSurfaceDim) },
            singleLine = false,
            maxLines = 5,
            shape = RoundedCornerShape(EdgeXRadius.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.accent,
            ),
        )
        EdgeXListGroup {
            EdgeXSwitchRow(
                title = stringResource(R.string.label_run_as_root),
                subtitle = stringResource(R.string.desc_run_as_root),
                checked = runAsRoot,
                onCheckedChange = { runAsRoot = it },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val trimmed = command.trim()
                if (trimmed.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.toast_shell_command_empty), Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (runAsRoot && containsSuCommand(trimmed)) {
                    Toast.makeText(context, context.getString(R.string.toast_shell_su_warning), Toast.LENGTH_LONG).show()
                }
                context.putConfigsSync(
                    prefKey to "shell:$runAsRoot:$trimmed",
                    "${prefKey}_label" to trimmed,
                    "${prefKey}_title" to trimmed,
                )
                Toast.makeText(context, context.getString(R.string.toast_shell_command_saved), Toast.LENGTH_SHORT).show()
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

private fun containsSuCommand(command: String): Boolean {
    val lines = command.split("\n", "\r\n", "\r")
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed == "su" || trimmed.startsWith("su ")) {
            return true
        }
    }
    return false
}
