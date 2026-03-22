package com.fan.edgex.hook

import android.view.InputEvent
import android.view.MotionEvent
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "EdgeX"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "android" -> hookInputManager(lpparam)
            "com.android.systemui" -> hookSystemUI(lpparam)
        }
    }

    /**
     * Hook InputManagerService.filterInputEvent in system_server
     * to intercept touch events at the input pipeline level.
     */
    private fun hookInputManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: Initializing filterInputEvent hook (system_server)")

        try {
            val inputManagerService = XposedHelpers.findClass(
                "com.android.server.input.InputManagerService", lpparam.classLoader
            )

            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val event = param.args[0] as InputEvent
                    if (event is MotionEvent) {
                        val context = XposedHelpers.getObjectField(param.thisObject, "mContext")
                            as android.content.Context
                        val consumed = GestureManager.handleMotionEvent(event, context)
                        if (consumed) {
                            param.setResult(false)
                        }
                    }
                }
            }

            var hooked = false

            // Attempt 1: filterInputEvent(InputEvent, int)
            if (!hooked) {
                try {
                    XposedHelpers.findAndHookMethod(
                        inputManagerService, "filterInputEvent",
                        InputEvent::class.java, Int::class.javaPrimitiveType, hook
                    )
                    hooked = true
                    XposedBridge.log("$TAG: Hooked filterInputEvent(InputEvent, int)")
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG: filterInputEvent(InputEvent, int) not found")
                }
            }

            // Attempt 2: filterInputEvent(InputEvent)
            if (!hooked) {
                try {
                    XposedHelpers.findAndHookMethod(
                        inputManagerService, "filterInputEvent",
                        InputEvent::class.java, hook
                    )
                    hooked = true
                    XposedBridge.log("$TAG: Hooked filterInputEvent(InputEvent)")
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG: filterInputEvent(InputEvent) not found")
                }
            }

            // Attempt 3: Reflective fallback - find any method named filterInputEvent
            if (!hooked) {
                for (m: Method in inputManagerService.declaredMethods) {
                    if (m.name == "filterInputEvent") {
                        try {
                            XposedBridge.hookMethod(m, hook)
                            hooked = true
                            XposedBridge.log("$TAG: Hooked filterInputEvent via reflection: ${m.parameterTypes.joinToString()}")
                            break
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Reflection hook failed: ${t.message}")
                        }
                    }
                }
            }

            if (!hooked) {
                XposedBridge.log("$TAG: ERROR - Failed to hook filterInputEvent with any method signature")
            }

        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Error during InputManagerService hook: ${t.message}")
        }
    }

    /**
     * Hook SystemUI to initialize overlay windows (DrawerWindow, debug views, etc.)
     * These need to run in the SystemUI process context for window management.
     */
    private fun hookSystemUI(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: Initializing SystemUI hook for overlay windows")

        XposedHelpers.findAndHookMethod(
            android.app.Application::class.java,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.thisObject as android.content.Context
                    GestureManager.initSystemUI(context)
                }
            }
        )
    }
}
