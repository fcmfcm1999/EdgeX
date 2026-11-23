package com.fan.edgex.hook

import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

internal enum class SystemBarType(val displayPolicyField: String) {
    STATUS_BAR("mStatusBar"),
    NAVIGATION_BAR("mNavigationBar"),
}

internal interface SystemBarBackend {
    /**
     * Returns whether the given system bar is currently hidden by policy,
     * or null if the platform state cannot be inspected.
     */
    fun isBarHidden(bar: SystemBarType): Boolean?

    /**
     * Requests WindowManager to show ([visible] == true) or hide ([visible] == false)
     * the given system bar. Returns true if the request was dispatched successfully.
     */
    fun setBarVisible(bar: SystemBarType, visible: Boolean): Boolean
}

internal class SystemBarStateController(
    private val backend: SystemBarBackend,
) {
    private var immersiveActive = false

    /**
     * Toggles immersive mode by hiding or showing both the status bar and navigation bar.
     *
     * @return `true` if immersive mode is now enabled (bars hidden),
     * `false` if disabled (bars visible), or `null` if neither bar could be updated.
     */
    @Synchronized
    fun toggleImmersiveMode(): Boolean? {
        val currentlyHidden = isImmersiveModeActive()
        val targetHidden = !currentlyHidden
        val visible = !targetHidden

        val statusApplied = backend.setBarVisible(SystemBarType.STATUS_BAR, visible)
        val navApplied = backend.setBarVisible(SystemBarType.NAVIGATION_BAR, visible)
        if (!statusApplied && !navApplied) return null

        immersiveActive = targetHidden
        return targetHidden
    }

    @Synchronized
    fun isImmersiveModeActive(): Boolean {
        val inspected = listOfNotNull(
            backend.isBarHidden(SystemBarType.STATUS_BAR),
            backend.isBarHidden(SystemBarType.NAVIGATION_BAR),
        )
        return if (inspected.isNotEmpty()) {
            inspected.all { it }
        } else {
            immersiveActive
        }
    }
}

internal object SystemBarController {
    private const val TAG = "EdgeX"

    private val controller = SystemBarStateController(WmSystemBarBackend())

    fun toggleImmersiveMode(): Boolean? = controller.toggleImmersiveMode()

    private class WmSystemBarBackend : SystemBarBackend {
        override fun isBarHidden(bar: SystemBarType): Boolean? = try {
            withDisplayPolicyBarWindow(bar) { barWindow ->
                readPolicyVisibility(barWindow)?.let { visible -> !visible }
            }
        } catch (_: Throwable) {
            null
        }

        override fun setBarVisible(bar: SystemBarType, visible: Boolean): Boolean = try {
            val result = withDisplayPolicyBarWindow(bar) { barWindow ->
                val methodName = if (visible) "show" else "hide"
                invokeWindowVisibilityMethod(barWindow, methodName)
            }
            if (result != true) {
                XposedBridge.log("$TAG: SystemBarController ${bar.displayPolicyField} not found")
            }
            result == true
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: SystemBarController failed to set ${bar.displayPolicyField} visible=$visible: ${t.message}")
            false
        }

        private fun <T> withDisplayPolicyBarWindow(bar: SystemBarType, block: (Any) -> T?): T? {
            val wms = getWindowManagerService() ?: return null
            val globalLock = XposedHelpers.getObjectField(wms, "mGlobalLock")
            synchronized(globalLock) {
                val displayContent = XposedHelpers.callMethod(wms, "getDefaultDisplayContentLocked")
                    ?: return null
                val displayPolicy = XposedHelpers.callMethod(displayContent, "getDisplayPolicy")
                    ?: return null
                val barWindow = XposedHelpers.getObjectField(displayPolicy, bar.displayPolicyField)
                    ?: return null
                return block(barWindow)
            }
        }

        private fun getWindowManagerService(): Any? {
            val serviceManager = XposedHelpers.findClass(
                "android.os.ServiceManager",
                ClassLoader.getSystemClassLoader(),
            )
            return XposedHelpers.callStaticMethod(serviceManager, "getService", "window")
        }

        private fun readPolicyVisibility(barWindow: Any): Boolean? {
            try {
                val result = XposedHelpers.callMethod(barWindow, "isVisibleByPolicy")
                if (result is Boolean) return result
            } catch (_: Throwable) {
            }
            try {
                return XposedHelpers.getBooleanField(barWindow, "mPolicyVisibilityAfterAnim")
            } catch (_: Throwable) {
            }
            try {
                return XposedHelpers.getBooleanField(barWindow, "mPolicyVisibility")
            } catch (_: Throwable) {
            }
            return null
        }

        private fun invokeWindowVisibilityMethod(barWindow: Any, methodName: String): Boolean {
            try {
                XposedHelpers.callMethod(barWindow, methodName, true, true)
                return true
            } catch (_: NoSuchMethodError) {
            } catch (_: XposedHelpers.InvocationTargetError) {
                throw IllegalStateException("InvocationTargetError calling $methodName(boolean, boolean)")
            }

            try {
                XposedHelpers.callMethod(barWindow, methodName, true)
                return true
            } catch (_: NoSuchMethodError) {
            }

            return false
        }
    }
}
