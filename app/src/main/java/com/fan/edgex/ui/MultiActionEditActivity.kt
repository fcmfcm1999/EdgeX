package com.fan.edgex.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.edgex.R
import com.fan.edgex.config.MultiAction
import com.fan.edgex.config.MultiActionStep
import com.fan.edgex.config.MultiActionStore
import com.fan.edgex.config.broadcastFullConfigSnapshot
import com.fan.edgex.config.configPrefs
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig

class MultiActionEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "multi_action_id"
        const val EXTRA_IS_NEW = "is_new"
    }

    private lateinit var multiActionId: String
    private lateinit var steps: MutableList<MultiActionStep>
    private lateinit var adapter: StepAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvTitle: TextView

    private var multiActionName = ""
    private var isModified = false
    private var isNew = false

    private var addingStep = false
    private var editingStepIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_action_edit)
        ThemeManager.applyToActivity(this)

        multiActionId = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        isNew = intent.getBooleanExtra(EXTRA_IS_NEW, false)

        if (isNew) {
            multiActionName = multiActionId
            steps = mutableListOf()
        } else {
            val existing = MultiActionStore.get(configPrefs(), multiActionId) ?: run { finish(); return }
            multiActionName = existing.name
            steps = existing.steps
        }

        tvTitle = findViewById(R.id.tv_title)
        tvTitle.text = multiActionName
        tvTitle.setOnClickListener { showRenameDialog() }

        recyclerView = findViewById(R.id.recycler_view)
        tvEmpty = findViewById(R.id.tv_empty)

        adapter = StepAdapter(steps)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                view.paddingLeft,
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top,
                view.paddingRight,
                view.paddingBottom,
            )
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { handleBack() }
        findViewById<View>(R.id.btn_save).setOnClickListener { saveAndShowToast() }

        val fab = findViewById<View>(R.id.btn_fab)
        fab.setOnClickListener { startAddStep() }
        fab.setOnApplyWindowInsetsListener { view, insets ->
            val navBottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            val lp = view.layoutParams as android.widget.FrameLayout.LayoutParams
            lp.bottomMargin = (16 * resources.displayMetrics.density + navBottom).toInt()
            view.layoutParams = lp
            insets
        }

        updateEmpty()
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applyToActivity(this)
        consumeTempStep()
    }

    private fun consumeTempStep() {
        val tempKey = MultiActionStore.tempStepKey()
        val code = getConfigString(tempKey)
        val label = getConfigString("${tempKey}_label")

        if (code.isBlank() || code == "none") {
            addingStep = false
            editingStepIndex = -1
            return
        }

        val step = MultiActionStep(code, label.ifBlank { code })

        when {
            addingStep -> {
                addingStep = false
                steps.add(step)
                adapter.notifyItemInserted(steps.size - 1)
                markModified()
            }
            editingStepIndex >= 0 -> {
                val idx = editingStepIndex
                editingStepIndex = -1
                steps[idx] = step
                adapter.notifyItemChanged(idx)
                markModified()
            }
        }

        putConfig(tempKey, "")
        putConfig("${tempKey}_label", "")
        updateEmpty()
    }

    private fun startAddStep() {
        addingStep = true
        editingStepIndex = -1
        putConfig(MultiActionStore.tempStepKey(), "")
        putConfig("${MultiActionStore.tempStepKey()}_label", "")
        startActivity(Intent(this, ActionSelectionActivity::class.java)
            .putExtra("pref_key", MultiActionStore.tempStepKey())
            .putExtra("title", getString(R.string.header_action_selection)))
    }

    private fun editStep(index: Int) {
        addingStep = false
        editingStepIndex = index
        val current = steps[index]
        putConfig(MultiActionStore.tempStepKey(), current.code)
        putConfig("${MultiActionStore.tempStepKey()}_label", current.label)
        startActivity(Intent(this, ActionSelectionActivity::class.java)
            .putExtra("pref_key", MultiActionStore.tempStepKey())
            .putExtra("title", current.label))
    }

    private fun showStepOptions(index: Int) {
        val step = steps[index]
        val options = arrayOf(
            getString(R.string.multi_action_step_edit_action),
            getString(R.string.multi_action_step_edit_icon_name),
            getString(R.string.multi_action_step_copy),
            getString(R.string.multi_action_step_execute),
            getString(R.string.multi_action_step_info),
            getString(R.string.multi_action_step_delete),
        )
        AlertDialog.Builder(this)
            .setTitle(step.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editStep(index)
                    1 -> showEditIconNameDialog(index)
                    2 -> copyStep(index)
                    3 -> executeStep(step)
                    4 -> showStepInfo(step)
                    5 -> deleteStep(index)
                }
            }
            .show()
    }

    private fun showEditIconNameDialog(index: Int) {
        val step = steps[index]
        val editText = android.widget.EditText(this).apply {
            setText(step.label)
            selectAll()
            hint = getString(R.string.multi_action_step_edit_label_hint)
        }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(48, 16, 48, 0)
            addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.multi_action_step_edit_icon_name)
            .setView(container)
            .setPositiveButton(R.string.multi_action_save) { _, _ ->
                val newLabel = editText.text.toString().trim().ifBlank { step.label }
                steps[index] = step.copy(label = newLabel)
                adapter.notifyItemChanged(index)
                markModified()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun copyStep(index: Int) {
        val copy = steps[index].copy()
        steps.add(index + 1, copy)
        adapter.notifyItemInserted(index + 1)
        markModified()
        updateEmpty()
    }

    private fun executeStep(step: MultiActionStep) {
        try {
            when {
                step.code == "home" -> {
                    startActivity(Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                step.code == "recents" || step.code == "recent" -> {
                    @Suppress("DEPRECATION")
                    val statusBar = getSystemService("statusbar")
                    if (statusBar != null) {
                        val cls = Class.forName("android.app.StatusBarManager")
                        cls.getMethod("toggleRecentApps").invoke(statusBar)
                    }
                }
                step.code == "expand_notifications" || step.code == "notifications" -> {
                    @Suppress("DEPRECATION")
                    val statusBar = getSystemService("statusbar")
                    if (statusBar != null) {
                        val cls = Class.forName("android.app.StatusBarManager")
                        cls.getMethod("expandNotificationsPanel").invoke(statusBar)
                    }
                }
                step.code == "volume_up" -> {
                    (getSystemService(AUDIO_SERVICE) as android.media.AudioManager)
                        .adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                            android.media.AudioManager.ADJUST_RAISE,
                            android.media.AudioManager.FLAG_SHOW_UI)
                }
                step.code == "volume_down" -> {
                    (getSystemService(AUDIO_SERVICE) as android.media.AudioManager)
                        .adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                            android.media.AudioManager.ADJUST_LOWER,
                            android.media.AudioManager.FLAG_SHOW_UI)
                }
                step.code.startsWith("music_control:") -> {
                    val keyCode = when (step.code.removePrefix("music_control:")) {
                        "play_pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        "next"       -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                        "previous"   -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        "stop"       -> android.view.KeyEvent.KEYCODE_MEDIA_STOP
                        else -> -1
                    }
                    if (keyCode != -1) {
                        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                        val now = android.os.SystemClock.uptimeMillis()
                        am.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0))
                        am.dispatchMediaKeyEvent(android.view.KeyEvent(now, now + 10, android.view.KeyEvent.ACTION_UP, keyCode, 0))
                    }
                }
                step.code.startsWith("launch_app:") -> {
                    val pkg = step.code.removePrefix("launch_app:")
                    val intent = packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) startActivity(intent)
                    else Toast.makeText(this, getString(R.string.toast_app_not_found), Toast.LENGTH_SHORT).show()
                }
                step.code.startsWith("app_shortcut:") -> {
                    val parts = step.code.split(":", limit = 3)
                    if (parts.size == 3) {
                        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                        launcherApps.startShortcut(parts[1], parts[2], null, null, android.os.Process.myUserHandle())
                    }
                }
                else -> Toast.makeText(
                    this,
                    getString(R.string.multi_action_execute_not_supported, step.label),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.multi_action_execute_not_supported, step.label), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStepInfo(step: MultiActionStep) {
        AlertDialog.Builder(this)
            .setTitle(R.string.multi_action_step_info)
            .setMessage(
                "${getString(R.string.multi_action_info_label, step.label)}\n" +
                "${getString(R.string.multi_action_info_code, step.code)}"
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun deleteStep(index: Int) {
        steps.removeAt(index)
        adapter.notifyItemRemoved(index)
        markModified()
        updateEmpty()
    }

    private fun showRenameDialog() {
        val editText = android.widget.EditText(this).apply {
            setText(multiActionName)
            selectAll()
            hint = getString(R.string.multi_action_edit_name_hint)
        }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(48, 16, 48, 0)
            addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.multi_action_edit_name_title)
            .setView(container)
            .setPositiveButton(R.string.multi_action_save) { _, _ ->
                val newName = editText.text.toString().trim().ifBlank { multiActionName }
                multiActionName = newName
                tvTitle.text = multiActionName
                markModified()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun markModified() {
        isModified = true
    }

    private fun saveAndShowToast() {
        doSave()
        Toast.makeText(this, R.string.multi_action_saved, Toast.LENGTH_SHORT).show()
    }

    private fun doSave() {
        val updated = MultiAction(multiActionId, multiActionName, steps)
        MultiActionStore.save(configPrefs(), updated)
        broadcastFullConfigSnapshot()
        isModified = false
        isNew = false
    }

    private fun handleBack() {
        if (!isModified) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.multi_action_unsaved_title)
            .setMessage(R.string.multi_action_unsaved_message)
            .setPositiveButton(R.string.multi_action_save) { _, _ ->
                doSave()
                finish()
            }
            .setNegativeButton(R.string.multi_action_discard) { _, _ ->
                if (isNew) MultiActionStore.delete(configPrefs(), multiActionId)
                finish()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBack()
    }

    private fun updateEmpty() {
        val empty = steps.isEmpty()
        tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    inner class StepAdapter(
        private val items: MutableList<MultiActionStep>,
    ) : RecyclerView.Adapter<StepAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.icon)
            val tvLabel: TextView = v.findViewById(R.id.tv_label)
            val btnMore: View = v.findViewById(R.id.btn_more)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_multi_action_step, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val step = items[position]
            holder.tvLabel.text = step.label
            holder.icon.setImageResource(iconForStep(step))
            ThemeManager.applyToView(holder.itemView, this@MultiActionEditActivity)
            holder.itemView.setOnLongClickListener {
                showStepOptions(position)
                true
            }
            holder.btnMore.setOnClickListener { showStepOptions(position) }
        }

        override fun getItemCount() = items.size
    }

    private fun iconForStep(step: MultiActionStep): Int = when {
        step.code == "back" -> R.drawable.ic_arrow_back
        step.code == "home" -> R.drawable.ic_home
        step.code == "recents" || step.code == "recent" -> R.drawable.ic_recents
        step.code == "expand_notifications" -> R.drawable.ic_notifications
        step.code == "screenshot" -> R.drawable.ic_camera
        step.code == "lock_screen" -> R.drawable.ic_power
        step.code == "kill_app" -> R.drawable.ic_kill_app
        step.code == "clear_background" -> R.drawable.ic_clear_recent
        step.code == "freezer_drawer" -> R.drawable.ic_freezer
        step.code == "refreeze" -> R.drawable.ic_refreeze
        step.code == "clipboard" -> R.drawable.ic_paste
        step.code == "universal_copy" -> R.drawable.ic_content_copy
        step.code == "brightness_up" -> R.drawable.ic_brightness_up
        step.code == "brightness_down" -> R.drawable.ic_brightness_down
        step.code == "volume_up" -> R.drawable.ic_volume_up
        step.code == "volume_down" -> R.drawable.ic_volume_down
        step.code.startsWith("music_control:") -> R.drawable.ic_music
        step.code.startsWith("launch_app:") -> R.drawable.ic_launch_app
        step.code.startsWith("app_shortcut:") -> R.drawable.ic_app_shortcut
        step.code.startsWith("shell:") -> R.drawable.ic_terminal
        step.code.startsWith("multi_action:") -> R.drawable.ic_multi_action
        else -> R.drawable.ic_action_dot
    }
}
