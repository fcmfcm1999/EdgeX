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
        val CONTENT_URI: Uri = "content://$AUTHORITY".toUri()

        fun uriForKey(key: String): Uri = Uri.withAppendedPath(CONTENT_URI, key)
    }

    override fun onCreate(): Boolean = true

    // URI: content://com.fan.edgex.provider/{key}
    // Returns a single-row cursor with column "value".
    // Values are stored as strings; booleans written via ConfigStore are already "true"/"false".
    // Legacy fallback: if no string is found, try reading as a native boolean (migration path).
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        val key = uri.lastPathSegment ?: return null
        val prefs = context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

        val value: String = prefs.getString(key, null)
            ?: runCatching { prefs.getBoolean(key, false).toString() }.getOrDefault("")

        val cursor = MatrixCursor(arrayOf("value"))
        cursor.addRow(arrayOf(value))
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.fan.edgex.config"

    // ContentValues must contain "value" (String). Key comes from the URI path.
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val context = context ?: return null
        val key = uri.lastPathSegment ?: return null
        val value = values?.getAsString("value") ?: return null

        context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(key, value).commit()

        notifyObservers(context, key)
        return uriForKey(key)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val context = context ?: return 0
        val key = uri.lastPathSegment ?: return 0
        val value = values?.getAsString("value") ?: return 0

        context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(key, value).commit()

        notifyObservers(context, key)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val context = context ?: return 0
        val key = uri.lastPathSegment ?: return 0

        context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(key).commit()

        notifyObservers(context, key)
        return 1
    }

    private fun notifyObservers(context: Context, key: String) {
        try { context.contentResolver.notifyChange(uriForKey(key), null) } catch (_: Throwable) {}
        try { context.contentResolver.notifyChange(CONTENT_URI, null) } catch (_: Throwable) {}
    }
}
