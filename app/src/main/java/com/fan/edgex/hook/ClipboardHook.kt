package com.fan.edgex.hook

import android.content.ClipData
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Hooks ClipboardService to cache clipboard text without relying on
 * ClipboardManager.getPrimaryClip(), which is rejected by the clipboard
 * access-gate in Android 10+ for background/system callers.
 *
 * We hook the internal write path so every clipboard change is captured
 * regardless of which entry point was used.
 */
object ClipboardHook {

    private const val TAG = "EdgeX"

    fun installHook(classLoader: ClassLoader) {
        // Try internal storage methods first (more reliable across API levels)
        val hookedInternal = tryHookByName(classLoader, "com.android.server.clipboard.ClipboardService",
            "setPrimaryClipInternal",     // Android 12-15
            "setPrimaryClipInternalLocked" // older fallback
        )

        // Always also hook the public entry point as a belt-and-suspenders fallback
        val hookedPublic = tryHookByName(classLoader, "com.android.server.clipboard.ClipboardService",
            "setPrimaryClip"
        )

        if (!hookedInternal && !hookedPublic) {
            XposedBridge.log("$TAG: ClipboardHook — no hook point found")
        }
    }

    private fun tryHookByName(classLoader: ClassLoader, className: String, vararg methodNames: String): Boolean {
        return try {
            val cls = XposedHelpers.findClass(className, classLoader)
            var hooked = false
            for (name in methodNames) {
                try {
                    val count = XposedBridge.hookAllMethods(cls, name, clipHook).size
                    if (count > 0) {
                        XposedBridge.log("$TAG: ClipboardHook hooked $className#$name ($count overloads)")
                        hooked = true
                    }
                } catch (_: Throwable) { }
            }
            hooked
        } catch (_: Throwable) {
            false
        }
    }

    private val clipHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            // Find the ClipData argument regardless of overload signature
            val clip = param.args.firstOrNull { it is ClipData } as? ClipData ?: return
            val text = extractText(clip)
            ClipboardOverlay.onClipboardChanged(text)
        }
    }

    private fun extractText(clip: ClipData): String? {
        if (clip.itemCount == 0) return null
        // Prefer getText() — no context needed, no URI resolution
        val text = clip.getItemAt(0).text?.toString()?.trim()
        return if (text.isNullOrEmpty()) null else text
    }
}
