package com.fan.edgex.ui.compose.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fan.edgex.R
import com.fan.edgex.config.HardwareKeyConfig
import com.fan.edgex.config.putConfig
import com.fan.edgex.hook.HardwareKeyDetector
import kotlinx.coroutines.delay

@Composable
internal fun HardwareKeyDetectionDialog(onDetected: (Int) -> Unit, onDismiss: () -> Unit, onTimeout: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val detected = rememberUpdatedState(onDetected)
    val dismiss = rememberUpdatedState(onDismiss)
    val timeout = rememberUpdatedState(onTimeout)
    val session = remember { System.currentTimeMillis() + HardwareKeyConfig.DETECT_DURATION_MS }
    val localDetector = remember { HardwareKeyDetector() }

    DisposableEffect(context, owner, session) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val code = intent.getIntExtra(HardwareKeyConfig.EXTRA_CODE, 0)
                if (intent.getLongExtra(HardwareKeyConfig.EXTRA_SESSION, 0) == session && code > 0) {
                    detected.value(code)
                }
            }
        }
        // system_server sends the result; other apps cannot supply a key to this receiver.
        context.registerReceiver(receiver, IntentFilter(HardwareKeyConfig.ACTION_DETECTED), Context.RECEIVER_NOT_EXPORTED)
        context.putConfig(HardwareKeyConfig.DETECT_UNTIL, session.toString())
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) dismiss.value()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            context.putConfig(HardwareKeyConfig.DETECT_UNTIL, "0")
            context.unregisterReceiver(receiver)
            owner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(session) {
        delay((session - System.currentTimeMillis()).coerceAtLeast(0))
        timeout.value()
    }
    AlertDialog(
        modifier = Modifier.onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            if (native.action != KeyEvent.ACTION_DOWN && native.action != KeyEvent.ACTION_UP) return@onPreviewKeyEvent false
            val result = localDetector.handle(native.keyCode, native.deviceId,
                native.action == KeyEvent.ACTION_DOWN, native.repeatCount, native.isCanceled,
                session, System.currentTimeMillis())
            result.code?.let { detected.value(it) }
            result.consumed
        },
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.key_detect)) },
        text = { Text(stringResource(R.string.key_detect_instruction)) },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
