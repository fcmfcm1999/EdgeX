package com.fan.edgex.ui

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig

/**
 * Activity for configuring a shell command action.
 * User can enter a shell command and optionally choose to run as root (su).
 */
class ShellCommandActivity : AppCompatActivity() {

    private lateinit var editCommand: EditText
    private lateinit var checkRunAsRoot: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell_command)
        ThemeManager.applyToActivity(this)

        // Header Insets
        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.getInsets(android.view.WindowInsets.Type.statusBars()).top, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        editCommand = findViewById(R.id.edit_command)
        checkRunAsRoot = findViewById(R.id.check_run_as_root)

        val prefKey = intent.getStringExtra("pref_key") ?: "unknown"

        // Load existing config if editing
        val existingAction = getConfigString(prefKey)
        if (existingAction.startsWith("shell:")) {
            parseAndFillExisting(existingAction)
        }

        findViewById<View>(R.id.btn_save).setOnClickListener {
            saveCommand(prefKey)
        }
    }

    private fun parseAndFillExisting(action: String) {
        // Format: shell:{runAsRoot}:{command}
        // Example: shell:true:reboot or shell:false:echo hello
        val parts = action.removePrefix("shell:").split(":", limit = 2)
        if (parts.size == 2) {
            checkRunAsRoot.isChecked = parts[0] == "true"
            editCommand.setText(parts[1])
        }
    }

    private fun saveCommand(prefKey: String) {
        val command = editCommand.text.toString().trim()
        if (command.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_shell_command_empty), Toast.LENGTH_SHORT).show()
            return
        }

        // Warn if user typed 'su' explicitly when runAsRoot is already checked
        if (checkRunAsRoot.isChecked && containsSuCommand(command)) {
            Toast.makeText(this, getString(R.string.toast_shell_su_warning), Toast.LENGTH_LONG).show()
        }

        val runAsRoot = checkRunAsRoot.isChecked
        putConfig(prefKey, "shell:$runAsRoot:$command")
        putConfig("${prefKey}_label", getString(R.string.action_shell_command))

        Toast.makeText(this, getString(R.string.toast_shell_command_saved), Toast.LENGTH_SHORT).show()
        finish()
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
}
