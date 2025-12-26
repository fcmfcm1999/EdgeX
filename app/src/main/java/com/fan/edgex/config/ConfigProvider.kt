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
        selection: String?, // KEY
        selectionArgs: Array<out String>?, // Default Value
        sortOrder: String? // TYPE (string, boolean, int)
    ): Cursor? {
        val context = context ?: return null
        val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        val key = selection ?: return null
        val defValue = selectionArgs?.firstOrNull() ?: ""
        val type = sortOrder ?: "string"
        
        val cursor = MatrixCursor(arrayOf("value"))
        
        val value = when(type) {
            "boolean" -> prefs.getBoolean(key, defValue.toBoolean()).toString()
            "int" -> prefs.getInt(key, defValue.toIntOrNull() ?: 0).toString()
            else -> prefs.getString(key, defValue)
        }
        
        android.util.Log.d("EdgeX", "ConfigProvider.query - key: $key, type: $type, value: $value")
        
        cursor.addRow(arrayOf(value))
        return cursor
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.com.fan.edge.config"

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val context = context ?: return null
        val key = values?.getAsString("key")
        val value = values?.getAsString("value")
        
        if (key != null && value != null) {
            context.getSharedPreferences("config", Context.MODE_PRIVATE)
                .edit()
                .putString(key, value)
                .apply()
            
            context.contentResolver.notifyChange(CONTENT_URI, null) // Notify observers
            return Uri.withAppendedPath(CONTENT_URI, key)
        }
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
         val context = context ?: return 0
         val key = selection
         if (key != null) {
              context.getSharedPreferences("config", Context.MODE_PRIVATE)
                .edit()
                .remove(key)
                .apply()
              context.contentResolver.notifyChange(CONTENT_URI, null)
              return 1
         }
         return 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        // Treat update same as insert for this simple KV store
        val uriRes = insert(uri, values)
        return if (uriRes != null) 1 else 0
    }
}
