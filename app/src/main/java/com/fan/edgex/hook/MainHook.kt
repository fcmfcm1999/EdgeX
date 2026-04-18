package com.fan.edgex.hook

import android.os.Handler
import android.os.Looper
import android.view.InputEvent
import android.view.KeyEvent
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
        
        /**
         * Check if the current call was initiated by our own code.
         * This is used to detect injected events and skip processing them.
         * Following Xposed Edge Pro's approach.
         */
        fun isCalledByUs(): Boolean {
            val stackTrace = Throwable().stackTrace
            for (i in 2 until stackTrace.size) {
                // Check if our package is in the call stack
                if (stackTrace[i].className.startsWith("com.fan.edgex")) {
                    return true
                }
            }
            return false
        }
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

            // Debug: list all filter-related methods
            val methods = inputManagerService.declaredMethods
            XposedBridge.log("$TAG: InputManagerService filter methods:")
            methods.filter { it.name.contains("filter", ignoreCase = true) }
                .forEach { XposedBridge.log("$TAG:   ${it.name}(${it.parameterTypes.joinToString { t -> t.simpleName }})") }
            
            // Debug: also list inject-related methods
            XposedBridge.log("$TAG: InputManagerService inject/dispatch methods:")
            methods.filter { it.name.contains("inject", ignoreCase = true) || it.name.contains("dispatch", ignoreCase = true) }
                .forEach { XposedBridge.log("$TAG:   ${it.name}(${it.parameterTypes.joinToString { t -> t.simpleName }})") }
            
            // Debug: Hook filterPointerMotion to see if it's being called
            try {
                XposedHelpers.findAndHookMethod(
                    inputManagerService, "filterPointerMotion",
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            XposedBridge.log("$TAG: filterPointerMotion called: x=${param.args[0]}, y=${param.args[1]}")
                        }
                    }
                )
                XposedBridge.log("$TAG: Hooked filterPointerMotion")
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: filterPointerMotion hook failed: ${t.message}")
            }

            // Hook interceptKeyBeforeDispatching for key event interception
            // This is the primary method called by the input dispatcher for key events
            try {
                XposedHelpers.findAndHookMethod(
                    inputManagerService, "interceptKeyBeforeDispatching",
                    "android.os.IBinder",
                    KeyEvent::class.java,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // Check if this is our own injected event
                            if (isCalledByUs()) {
                                return  // Let original method handle it
                            }
                            
                            val keyEvent = param.args[1] as KeyEvent
                            
                            // Process key through KeyManager
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as android.content.Context
                            val consumed = GestureManager.handleKeyEvent(keyEvent, context, param)
                            if (consumed) {
                                // Return non-zero to consume the key (prevent system handling)
                                param.result = -1L
                            }
                        }
                    }
                )
                XposedBridge.log("$TAG: Hooked interceptKeyBeforeDispatching")
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: interceptKeyBeforeDispatching hook failed: ${t.message}")
            }

            // 2) Hook filterInputEvent to intercept touch and key events
            // This is called when InputFilter is enabled and receives all input events
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Check if this event was injected by us (call stack check)
                    // Following Xposed Edge Pro's approach
                    if (isCalledByUs()) {
                        XposedBridge.log("$TAG: Skipping event from our own injection (call stack)")
                        return  // Let original method handle it
                    }
                    
                    val event = param.args[0] as InputEvent
                    val context = XposedHelpers.getObjectField(param.thisObject, "mContext")
                        as android.content.Context
                    
                    when (event) {
                        is MotionEvent -> {
                            val consumed = GestureManager.handleMotionEvent(event, context)
                            if (consumed) {
                                param.setResult(false)
                            }
                        }
                        is KeyEvent -> {
                            // Get policyFlags (second parameter if available)
                            val policyFlags = if (param.args.size > 1 && param.args[1] is Int) {
                                param.args[1] as Int
                            } else {
                                0
                            }
                            val consumed = GestureManager.handleKeyEvent(event, context, param, policyFlags)
                            XposedBridge.log("$TAG: filterInputEvent KeyEvent keyCode=${event.keyCode} action=${event.action} consumed=$consumed")
                            if (consumed) {
                                // Consume the event (don't let system handle it)
                                XposedBridge.log("$TAG: Setting result=false to drop event")
                                param.setResult(false)
                            } else {
                                XposedBridge.log("$TAG: Letting original filterInputEvent handle the event")
                            }
                            // If not consumed (including injected events we're skipping):
                            // Do nothing - let original filterInputEvent run
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
        // Store mNative reference to enable filter after InputManagerService is instantiated
        var mNativeInstance: Any? = null
        var inputManagerServiceInstance: Any? = null

        // Hook setInputFilter to see when filters are set
        try {
            XposedHelpers.findAndHookMethod(
                inputManagerService, "setInputFilter",
                "android.view.IInputFilter",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val filter = param.args[0]
                        XposedBridge.log("$TAG: setInputFilter called with filter=${filter?.javaClass?.name ?: "null"}")
                    }
                }
            )
            XposedBridge.log("$TAG: Hooked setInputFilter")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook setInputFilter: ${t.message}")
        }

        // Android 16+: NativeInputManagerService$NativeImpl
        try {
            val nativeImplClass = XposedHelpers.findClass(
                "com.android.server.input.NativeInputManagerService\$NativeImpl",
                classLoader
            )

            // Hook setInputFilterEnabled to always force true when called
            XposedHelpers.findAndHookMethod(nativeImplClass, "setInputFilterEnabled",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // We no longer need to force InputFilter enabled since we use
                        // interceptKeyBeforeDispatching for keys and overlay for gestures
                    }
                })

            // Hook InputManagerService constructor to get mNative field reference
            XposedHelpers.findAndHookConstructor(
                inputManagerService,
                android.content.Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        inputManagerServiceInstance = param.thisObject
                        mNativeInstance = XposedHelpers.getObjectField(param.thisObject, "mNative")
                        XposedBridge.log("$TAG: Got InputManagerService and mNative instances")
                    }
                })

            // Also hook start() for any post-initialization needed
            XposedHelpers.findAndHookMethod(inputManagerService, "start",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // Note: We previously tried to register a fake IInputFilter here,
                        // but this caused all touch events to be blocked because the
                        // Binder.onTransact hook interfered with system Binder calls.
                        // 
                        // Key interception works via interceptKeyBeforeDispatching hook.
                        // Touch gestures work via the overlay window in SystemUI.
                        
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
