package com.fan.edgex.hook

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Helper class to perform global actions (back, home, recents, etc.) by creating
 * a fake AccessibilityService that connects to the system's AccessibilityManager.
 * 
 * This technique is borrowed from Xposed Edge Pro, which uses reflection to:
 * 1. Get the internal IAccessibilityManager service from AccessibilityManager
 * 2. Get a connection ID from the service's InteractionBridge
 * 3. Set that connection ID on a fake AccessibilityService instance
 * 4. Call performGlobalAction() on the fake service
 */
object GlobalActionHelper {

    private const val TAG = "EdgeX"

    // Global action constants (from AccessibilityService)
    const val GLOBAL_ACTION_BACK = 1
    const val GLOBAL_ACTION_HOME = 2
    const val GLOBAL_ACTION_RECENTS = 3
    const val GLOBAL_ACTION_NOTIFICATIONS = 4
    const val GLOBAL_ACTION_QUICK_SETTINGS = 5
    const val GLOBAL_ACTION_POWER_DIALOG = 6
    const val GLOBAL_ACTION_LOCK_SCREEN = 8
    const val GLOBAL_ACTION_TAKE_SCREENSHOT = 9
    const val GLOBAL_ACTION_PASTE = 11

    private var fakeService: FakeAccessibilityService? = null

    /**
     * Perform a global action like back, home, recents, etc.
     * Must be called from system_server process.
     */
    fun performGlobalAction(context: Context, action: Int): Boolean {
        try {
            val service = getOrCreateFakeService(context)
            if (service != null) {
                return service.performGlobalAction(action)
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to perform global action $action: ${e.message}")
        }
        return false
    }

    private fun getOrCreateFakeService(context: Context): FakeAccessibilityService? {
        if (fakeService != null) return fakeService

        try {
            val service = FakeAccessibilityService(context)
            fakeService = service
            return service
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to create fake accessibility service: ${e.message}")
            return null
        }
    }

    /**
     * A fake AccessibilityService that can call performGlobalAction without
     * being registered as a real accessibility service.
     */
    private class FakeAccessibilityService(context: Context) : AccessibilityService() {

        init {
            // Get AccessibilityManager
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

            // Get the internal IAccessibilityManager service
            val service = getAccessibilityManagerService(am)

            // Get connection ID from the service
            val connectionId = getConnectionId(service)

            // Set connection ID on this fake service
            XposedHelpers.setIntField(this, "mConnectionId", connectionId)

            // Attach base context
            attachBaseContext(context)

            XposedBridge.log("$TAG: FakeAccessibilityService created with connectionId=$connectionId")
        }

        private fun getAccessibilityManagerService(am: AccessibilityManager): Any {
            try {
                // Try to get mService field first
                val service = XposedHelpers.getObjectField(am, "mService")
                if (service != null) return service
            } catch (e: Throwable) {
                // Fallback: try getServiceLocked
            }

            // Fallback: call getServiceLocked() 
            synchronized(XposedHelpers.getObjectField(am, "mLock")) {
                return XposedHelpers.callMethod(am, "getServiceLocked")
            }
        }

        private fun getConnectionId(service: Any): Int {
            val sdkVersion = Build.VERSION.SDK_INT

            return if (sdkVersion >= 26) {
                // API 26+: Use getInteractionBridge()
                val bridge = XposedHelpers.callMethod(service, "getInteractionBridge")
                XposedHelpers.getIntField(bridge, "mConnectionId")
            } else if (sdkVersion >= 21) {
                // API 21-25: Use getInteractionBridgeLocked()
                val lock = XposedHelpers.getObjectField(service, "mLock")
                synchronized(lock) {
                    val bridge = XposedHelpers.callMethod(service, "getInteractionBridgeLocked")
                    XposedHelpers.getIntField(bridge, "mConnectionId")
                }
            } else {
                // API < 21: Use getQueryBridge()
                val lock = XposedHelpers.getObjectField(service, "mLock")
                synchronized(lock) {
                    val bridge = XposedHelpers.callMethod(service, "getQueryBridge")
                    XposedHelpers.getIntField(bridge, "mId")
                }
            }
        }

        override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
            // Not used
        }

        override fun onInterrupt() {
            // Not used
        }
    }
}
