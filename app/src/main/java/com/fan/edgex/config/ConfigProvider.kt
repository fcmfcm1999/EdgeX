package com.fan.edgex.config

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.core.net.toUri

class ConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.fan.edgex.provider"
        val CONTENT_URI: Uri = "content://$AUTHORITY/config".toUri()
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        // Read from internal standardized SharedPreferences
        val context = context ?: return null
        val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        val selectedApps = prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
        
        // Return data as a Cursor
        val cursor = MatrixCursor(arrayOf("package_names"))
        cursor.addRow(arrayOf(selectedApps.joinToString(",")))
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.fan.edge.config"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
