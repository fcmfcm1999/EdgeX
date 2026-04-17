package com.fan.edgex.hook

import android.os.Handler
import android.os.Looper
import android.view.InputEvent
import android.view.MotionEvent
import com.fan.edgex.BuildConfig
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
     *
     * Also enables InputFilter via nativeSetInputFilterEnabled so that
     * the native InputDispatcher actually calls filterInputEvent.
     */
    private fun hookInputManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: Initializing filterInputEvent hook (system_server)")

        try {
            val inputManagerService = XposedHelpers.findClass(
                "com.android.server.input.InputManagerService", lpparam.classLoader
            )

            // 1) Hook filterInputEvent to intercept touch events
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

            // Attempt 3: Reflective fallback
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

            // 2) Enable InputFilter so native InputDispatcher calls filterInputEvent
            enableInputFilter(inputManagerService, lpparam.classLoader)
            UniversalCopyManager.installHooks(lpparam.classLoader)

        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Error during InputManagerService hook: ${t.message}")
        }
    }

    /**
     * Enable InputFilter so that the native InputDispatcher calls filterInputEvent.
     * Without this, filterInputEvent is never invoked because InputFilterEnabled defaults to false.
     *
     * Android 16+: NativeInputManagerService$NativeImpl.setInputFilterEnabled(boolean)
     * Legacy:      InputManagerService.nativeSetInputFilterEnabled(long, boolean)
     */
    private fun enableInputFilter(inputManagerService: Class<*>, classLoader: ClassLoader) {
        // Android 16+: NativeInputManagerService$NativeImpl
        try {
            val nativeImplClass = XposedHelpers.findClass(
                "com.android.server.input.NativeInputManagerService\$NativeImpl",
                classLoader
            )

            // Hook setInputFilterEnabled to always force true
            XposedHelpers.findAndHookMethod(nativeImplClass, "setInputFilterEnabled",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = true
                    }
                })

            // Hook start() to initially enable InputFilter
            XposedHelpers.findAndHookMethod(nativeImplClass, "start",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            XposedHelpers.callMethod(param.thisObject, "setInputFilterEnabled", true)
                            XposedBridge.log("$TAG: InputFilter enabled via NativeImpl.start()")
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Failed to enable InputFilter: ${t.message}")
                        }
                        if (BuildConfig.ENABLE_AUTO_SYSTEMUI_RESTART) {
                            XposedBridge.log("$TAG: Auto SystemUI restart enabled for local development")
                            scheduleSystemUIRestart()
                        }
                    }
                })

            XposedBridge.log("$TAG: Hooked NativeImpl for InputFilter control (Android 16+)")
            return
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: NativeImpl not found, trying legacy approach: ${t.message}")
        }

        // Legacy: nativeSetInputFilterEnabled(long ptr, boolean enable)
        try {
            val nativeMethod = inputManagerService.getDeclaredMethod(
                "nativeSetInputFilterEnabled",
                Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            nativeMethod.isAccessible = true

            XposedBridge.hookMethod(nativeMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[param.args.size - 1] = true
                }
            })

            XposedHelpers.findAndHookMethod(inputManagerService, "start",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val ptr = XposedHelpers.getLongField(param.thisObject, "mPtr")
                            nativeMethod.invoke(null, ptr, true)
                            XposedBridge.log("$TAG: InputFilter enabled via legacy nativeSetInputFilterEnabled")
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Legacy InputFilter enable failed: ${t.message}")
                        }
                    }
                })

            XposedBridge.log("$TAG: Hooked legacy nativeSetInputFilterEnabled for InputFilter control")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: All InputFilter enable approaches failed: ${t.message}")
        }
    }

    /**
     * Local-development workaround for environments where SystemUI injection
     * is missing after boot. Disabled by default and only enabled via build flag.
     */
    private fun scheduleSystemUIRestart() {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val activityThread = Class.forName("android.app.ActivityThread")
                val currentApp = activityThread.getMethod("currentApplication").invoke(null)
                if (currentApp != null) {
                    val ctx = currentApp as android.content.Context
                    val activityManager = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                        as android.app.ActivityManager
                    val processes = activityManager.runningAppProcesses
                    val systemUIProcess = processes?.find { it.processName == "com.android.systemui" }
                    if (systemUIProcess != null) {
                        XposedBridge.log("$TAG: Restarting SystemUI (PID ${systemUIProcess.pid}) for local development")
                        android.os.Process.killProcess(systemUIProcess.pid)
                    }
                }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: SystemUI restart failed: ${t.message}")
            }
        }, 15000)
    }

    /**
     * Hook SystemUI to initialize overlay windows (DrawerWindow, debug views, etc.)
     * These need to run in the SystemUI process context for window management.
     */
    private fun hookSystemUI(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: Initializing SystemUI hook for overlay windows")

        try {
            XposedHelpers.findAndHookMethod(
                android.app.Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val context = param.thisObject as android.content.Context
                            GestureManager.initSystemUI(context)
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Error in SystemUI initSystemUI: ${t.message}")
                            t.printStackTrace()
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook SystemUI Application.onCreate: ${t.message}")
        }
    }
}
