package com.fan.edgex.overlay

import android.content.Context
import android.graphics.drawable.Drawable
import com.fan.edgex.hook.ModuleRes

internal enum class QuickSettingsIcon {
    WIFI,
    BLUETOOTH,
    AIRPLANE,
    HOTSPOT,
    BRIGHTNESS,
    VOLUME,
    DO_NOT_DISTURB,
    FLASHLIGHT,
    ROTATION_LOCK,
    CAMERA,
    MEDIA_PREVIOUS,
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
}

/** Loads the device's own framework/SystemUI glyphs before using a module fallback. */
internal class SystemQuickSettingsIconResolver(private val context: Context) {
    private val systemUiContext by lazy {
        runCatching {
            context.createPackageContext(
                SYSTEM_UI_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_RESTRICTED,
            )
        }.getOrNull()
    }

    fun load(icon: QuickSettingsIcon, fallbackRes: Int): Drawable? {
        val candidates = names.getValue(icon)
        loadFrom(systemUiContext, SYSTEM_UI_PACKAGE, candidates)?.let { return it }
        loadFrom(context, ANDROID_PACKAGE, candidates)?.let { return it }
        return ModuleRes.getDrawable(fallbackRes)?.mutate()
    }

    private fun loadFrom(source: Context?, packageName: String, candidates: List<String>): Drawable? {
        val resources = source?.resources ?: return null
        for (name in candidates) {
            val id = resources.getIdentifier(name, "drawable", packageName)
            if (id == 0) continue
            runCatching { resources.getDrawable(id, source.theme).mutate() }.getOrNull()?.let { return it }
        }
        return null
    }

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val ANDROID_PACKAGE = "android"

        val names = mapOf(
            QuickSettingsIcon.WIFI to listOf("ic_wifi_3", "ic_qs_wifi_disconnected", "ic_qs_wifi", "ic_wifi_signal_4"),
            QuickSettingsIcon.BLUETOOTH to listOf("ic_qs_bluetooth", "ic_bluetooth_connected", "stat_sys_data_bluetooth"),
            QuickSettingsIcon.AIRPLANE to listOf("ic_qs_airplane", "ic_airplane_mode_on", "stat_sys_airplane_mode"),
            QuickSettingsIcon.HOTSPOT to listOf("ic_qs_hotspot", "ic_hotspot", "ic_wifi_tethering"),
            QuickSettingsIcon.BRIGHTNESS to listOf("ic_brightness", "ic_brightness_full", "ic_brightness_medium"),
            QuickSettingsIcon.VOLUME to listOf("ic_volume_media", "ic_audio_vol", "ic_volume_up"),
            QuickSettingsIcon.DO_NOT_DISTURB to listOf("ic_qs_dnd_on", "ic_dnd", "ic_do_not_disturb_on"),
            QuickSettingsIcon.FLASHLIGHT to listOf("ic_qs_flashlight", "ic_signal_flashlight", "ic_flashlight"),
            QuickSettingsIcon.ROTATION_LOCK to listOf("ic_qs_auto_rotate", "ic_screen_rotation", "ic_qs_rotation_lock"),
            QuickSettingsIcon.CAMERA to listOf("ic_camera_alt", "ic_camera", "ic_qs_camera"),
            QuickSettingsIcon.MEDIA_PREVIOUS to listOf("ic_media_previous", "ic_skip_previous"),
            QuickSettingsIcon.MEDIA_PLAY_PAUSE to listOf("ic_media_play", "ic_media_pause", "ic_play_arrow"),
            QuickSettingsIcon.MEDIA_NEXT to listOf("ic_media_next", "ic_skip_next"),
        )
    }
}
