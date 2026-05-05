package com.fan.edgex.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import com.fan.edgex.R
import java.io.File
import java.util.UUID

object MultiActionIconUtils {
    const val PREFIX_APP = "app:"
    const val PREFIX_CUSTOM = "custom:"

    fun resolveDrawable(context: Context, iconRef: String): Drawable? = when {
        iconRef.startsWith(PREFIX_APP) -> loadAppIcon(context, iconRef.removePrefix(PREFIX_APP))
        iconRef.startsWith(PREFIX_CUSTOM) -> {
            val bmp = loadCustomBitmap(context, iconRef.removePrefix(PREFIX_CUSTOM))
            bmp?.let { BitmapDrawable(context.resources, it) }
        }
        else -> null
    }

    fun loadAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun loadCustomBitmap(context: Context, filename: String): Bitmap? {
        val file = File(context.filesDir, "multi_action_icons/$filename")
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun saveCustomIconFromUri(context: Context, uri: Uri): String? {
        val iconDir = File(context.filesDir, "multi_action_icons")
        iconDir.mkdirs()
        val filename = "${UUID.randomUUID()}.png"
        val file = File(iconDir, filename)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            filename
        }.getOrNull()
    }

    fun deleteIfCustom(context: Context, iconRef: String) {
        if (!iconRef.startsWith(PREFIX_CUSTOM)) return
        File(context.filesDir, "multi_action_icons/${iconRef.removePrefix(PREFIX_CUSTOM)}").delete()
    }

    /**
     * Apply iconRef to an ImageView sitting inside a themed circle background.
     * - Default (empty): shows ic_multi_action with white tint
     * - App/Custom: shows the actual drawable with no tint so colors are preserved
     */
    fun applyTo(context: Context, imageView: ImageView, iconRef: String) {
        val drawable = if (iconRef.isNotEmpty()) resolveDrawable(context, iconRef) else null
        if (drawable != null) {
            imageView.setImageDrawable(drawable)
            imageView.imageTintList = null
            imageView.clearColorFilter()
        } else {
            imageView.setImageResource(R.drawable.ic_multi_action)
            imageView.imageTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
    }
}
