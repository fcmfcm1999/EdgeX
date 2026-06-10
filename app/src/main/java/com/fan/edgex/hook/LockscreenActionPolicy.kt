package com.fan.edgex.hook

internal object LockscreenActionPolicy {
    fun requiresUnlock(action: String): Boolean = when {
        action == "home" -> true
        action == "recent" || action == "recents" -> true
        action == "kill_app" -> true
        action == "prev_app" || action == "next_app" -> true
        action == "clipboard" || action == "universal_copy" -> true
        action.startsWith("fast_scroll:") -> true
        action.startsWith("launch_app:") -> true
        else -> false
    }
}
