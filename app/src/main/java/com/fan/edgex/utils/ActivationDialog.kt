package com.fan.edgex.utils

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.fan.edgex.R
import com.fan.edgex.license.PremiumActivator
import kotlin.concurrent.thread

object ActivationDialog {
    fun show(context: Context, onActivated: (() -> Unit)? = null) {
        val input = EditText(context).apply {
            hint = context.getString(R.string.premium_activation_hint)
            isSingleLine = true
            setSelectAllOnFocus(true)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.premium_activation_title)
            .setMessage(R.string.premium_activation_message)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.premium_activate, null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val code = input.text?.toString().orEmpty()
                button.isEnabled = false
                input.isEnabled = false
                dialog.setMessage(context.getString(R.string.premium_activation_in_progress))
                hideKeyboard(context, input)

                thread(name = "EdgeXPremiumActivation") {
                    val result = PremiumActivator.activate(context.applicationContext, code)
                    input.post {
                        result.onSuccess {
                            Toast.makeText(
                                context,
                                R.string.premium_activation_success,
                                Toast.LENGTH_LONG,
                            ).show()
                            onActivated?.invoke()
                            dialog.dismiss()
                        }.onFailure {
                            button.isEnabled = true
                            input.isEnabled = true
                            dialog.setMessage(
                                context.getString(
                                    R.string.premium_activation_failed,
                                    activationFailureMessage(context, it),
                                ),
                            )
                        }
                    }
                }
            }
            input.requestFocus()
            input.post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        dialog.show()
    }

    private fun activationFailureMessage(context: Context, throwable: Throwable): String {
        val rawMessage = throwable.message.orEmpty()
        val message = rawMessage.lowercase()
        return when {
            message.contains("activation code is empty") ->
                context.getString(R.string.premium_activation_error_empty_code)
            message.contains("invalid code") || message.contains("activation failed (404)") ->
                context.getString(R.string.premium_activation_error_invalid_code)
            message.contains("api url is not configured") ->
                context.getString(R.string.premium_activation_error_not_configured)
            message.contains("downloaded premium dex hash mismatch") ->
                context.getString(R.string.premium_activation_error_package_verify)
            message.contains("unsupported premium version") ->
                context.getString(R.string.premium_activation_error_unsupported_version)
            message.contains("download failed") ->
                context.getString(R.string.premium_activation_error_download)
            message.contains("root install failed") || message.contains("permission denied") ->
                context.getString(R.string.premium_activation_error_root)
            message.contains("timeout") ||
                message.contains("failed to connect") ||
                message.contains("unable to resolve host") ||
                throwable.javaClass.name.startsWith("java.net.") ->
                context.getString(R.string.premium_activation_error_network)
            message.contains("activation failed (") || message.contains("request failed (") ->
                context.getString(R.string.premium_activation_error_server)
            else -> rawMessage.ifBlank { throwable.javaClass.simpleName }
        }
    }

    private fun hideKeyboard(context: Context, input: EditText) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(input.windowToken, 0)
    }
}
