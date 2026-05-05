package com.fan.edgex.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.edgex.R
import com.fan.edgex.config.MultiAction
import com.fan.edgex.config.MultiActionStore
import com.fan.edgex.config.broadcastFullConfigSnapshot
import com.fan.edgex.config.configPrefs
import com.fan.edgex.config.putConfig
import com.fan.edgex.config.requestHookActionExecution

class MultiActionsListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_PICK = "pick"
        const val EXTRA_PREF_KEY = "pref_key"
        const val EXTRA_TITLE = "title"
    }

    private lateinit var adapter: MultiActionAdapter
    private val items = mutableListOf<MultiAction>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private var pickMode = false
    private var prefKey = ""

    private var pendingIconMultiAction: MultiAction? = null
    private val iconPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val action = pendingIconMultiAction ?: return@registerForActivityResult
        pendingIconMultiAction = null
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val newRef = result.data?.getStringExtra(AppIconPickerActivity.EXTRA_ICON_REF) ?: return@registerForActivityResult
            MultiActionIconUtils.deleteIfCustom(this, action.iconRef)
            MultiActionStore.save(configPrefs(), action.copy(iconRef = newRef))
            broadcastFullConfigSnapshot()
            loadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_actions_list)
        ThemeManager.applyToActivity(this)

        pickMode = intent.getStringExtra(EXTRA_MODE) == MODE_PICK
        prefKey = intent.getStringExtra(EXTRA_PREF_KEY) ?: ""

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                view.paddingLeft,
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top,
                view.paddingRight,
                view.paddingBottom,
            )
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.recycler_view)
        tvEmpty = findViewById(R.id.tv_empty)

        adapter = MultiActionAdapter(items,
            onClick = { multiAction ->
                if (pickMode) {
                    pickAndReturn(multiAction)
                } else {
                    openEdit(multiAction.id)
                }
            },
            onLongClick = { multiAction ->
                showItemOptions(multiAction)
            },
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val fab = findViewById<View>(R.id.btn_fab)
        fab.setOnClickListener { createNew() }
        fab.setOnApplyWindowInsetsListener { view, insets ->
            val navBottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            val lp = view.layoutParams as android.widget.FrameLayout.LayoutParams
            lp.bottomMargin = (16 * resources.displayMetrics.density + navBottom).toInt()
            view.layoutParams = lp
            insets
        }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applyToActivity(this)
        loadData()
    }

    private fun loadData() {
        val prefs = configPrefs()
        items.clear()
        items.addAll(MultiActionStore.getAll(prefs))
        adapter.notifyDataSetChanged()
        updateEmpty()
    }

    private fun updateEmpty() {
        val empty = items.isEmpty()
        tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun createNew() {
        val id = MultiActionStore.generateId()
        startActivity(Intent(this, MultiActionEditActivity::class.java)
            .putExtra(MultiActionEditActivity.EXTRA_ID, id)
            .putExtra(MultiActionEditActivity.EXTRA_IS_NEW, true))
    }

    private fun openEdit(id: String) {
        startActivity(Intent(this, MultiActionEditActivity::class.java)
            .putExtra(MultiActionEditActivity.EXTRA_ID, id))
    }

    private fun pickAndReturn(multiAction: MultiAction) {
        if (prefKey.isNotBlank()) {
            putConfig(prefKey, MultiActionStore.actionCode(multiAction.id))
            putConfig("${prefKey}_label", multiAction.name)
        }
        finish()
    }

    private fun showItemOptions(multiAction: MultiAction) {
        val options = arrayOf(
            getString(R.string.action_edit),
            getString(R.string.action_rename),
            getString(R.string.multi_action_option_edit_icon),
            getString(R.string.action_execute),
            getString(R.string.action_delete),
        )
        AlertDialog.Builder(this)
            .setTitle(multiAction.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openEdit(multiAction.id)
                    1 -> showRenameDialog(multiAction)
                    2 -> openIconPicker(multiAction)
                    3 -> requestHookActionExecution(MultiActionStore.actionCode(multiAction.id))
                    4 -> showDeleteConfirm(multiAction)
                }
            }
            .show()
    }

    private fun openIconPicker(multiAction: MultiAction) {
        pendingIconMultiAction = multiAction
        iconPickerLauncher.launch(Intent(this, AppIconPickerActivity::class.java))
    }

    private fun showRenameDialog(multiAction: MultiAction) {
        val editText = android.widget.EditText(this).apply {
            setText(multiAction.name)
            selectAll()
            hint = getString(R.string.multi_action_edit_name_hint)
        }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(48, 16, 48, 0)
            addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.action_rename)
            .setView(container)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val newName = editText.text.toString().trim().ifBlank { multiAction.name }
                val updated = multiAction.copy(name = newName)
                MultiActionStore.save(configPrefs(), updated)
                broadcastFullConfigSnapshot()
                loadData()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirm(multiAction: MultiAction) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.multi_action_option_delete_confirm, multiAction.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                MultiActionStore.delete(configPrefs(), multiAction.id)
                broadcastFullConfigSnapshot()
                loadData()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    inner class MultiActionAdapter(
        private val items: List<MultiAction>,
        private val onClick: (MultiAction) -> Unit,
        private val onLongClick: (MultiAction) -> Unit,
    ) : RecyclerView.Adapter<MultiActionAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tv_name)
            val tvCount: TextView = v.findViewById(R.id.tv_count)
            val icon: ImageView = v.findViewById(R.id.icon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_multi_action, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.tvCount.text = getString(R.string.multi_action_step_count, item.steps.size)
            ThemeManager.applyToView(holder.itemView, this@MultiActionsListActivity)
            MultiActionIconUtils.applyTo(this@MultiActionsListActivity, holder.icon, item.iconRef)
            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
