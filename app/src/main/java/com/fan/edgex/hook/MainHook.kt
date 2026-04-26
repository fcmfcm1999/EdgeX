package com.fan.edgex.hook

import android.os.Handler
import android.os.Looper
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.fan.edgex.BuildConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        ModuleRes.init(startupParam.modulePath)
    }

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
        try {
            val inputManagerService = XposedHelpers.findClass(
                "com.android.server.input.InputManagerService", lpparam.classLoader
            )

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
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: interceptKeyBeforeDispatching hook failed: ${t.message}")
            }

            // 2) Hook filterInputEvent to intercept touch and key events
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isCalledByUs()) return

                    val event = param.args[0] as InputEvent
                    val context = XposedHelpers.getObjectField(param.thisObject, "mContext")
                        as android.content.Context

                    when (event) {
                        is MotionEvent -> {
                            if (GestureManager.handleMotionEvent(event, context)) {
                                param.setResult(false)
                            }
                        }
                        is KeyEvent -> {
                            val policyFlags = if (param.args.size > 1 && param.args[1] is Int) {
                                param.args[1] as Int
                            } else {
                                0
                            }
                            if (GestureManager.handleKeyEvent(event, context, param, policyFlags)) {
                                param.setResult(false)
                            }
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
                } catch (t: Throwable) {
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
                } catch (t: Throwable) {
                }
            }

            // Attempt 3: Reflective fallback
            if (!hooked) {
                for (m: Method in inputManagerService.declaredMethods) {
                    if (m.name == "filterInputEvent") {
                        try {
                            XposedBridge.hookMethod(m, hook)
                            hooked = true
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

        // Hook setInputFilter: when a real filter is set (e.g. accessibility service),
        // our fake filter is not needed. When the real filter is removed (set to null),
        // re-register our fake filter so filterInputEvent keeps firing.
        try {
            XposedHelpers.findAndHookMethod(
                inputManagerService, "setInputFilter",
                "android.view.IInputFilter",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args[0] == null) {
                            registerFakeInputFilter(param.thisObject, inputManagerService.classLoader!!)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook setInputFilter: ${t.message}")
        }

        // Android 16+: NativeInputManagerService$NativeImpl
        try {
            val nativeImplClass = XposedHelpers.findClass(
                "com.android.server.input.NativeInputManagerService\$NativeImpl",
                classLoader
            )

            // Force InputFilter always enabled — prevents accessibility/system from disabling it
            XposedHelpers.findAndHookMethod(nativeImplClass, "setInputFilterEnabled",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = true
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
                    }
                })

            // Hook start() to enable InputFilter after native layer is ready
            XposedHelpers.findAndHookMethod(inputManagerService, "start",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext")
                                as android.content.Context
                            GestureManager.initSystemServer(context)
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Failed to initialize GestureManager in start(): ${t.message}")
                        }

                        try {
                            val native = mNativeInstance
                            if (native != null) {
                                XposedHelpers.callMethod(native, "setInputFilterEnabled", true)
                            } else {
                                XposedBridge.log("$TAG: mNative is null at start(), cannot enable InputFilter")
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Failed to enable InputFilter in start(): ${t.message}")
                        }

                        // Register a fake IInputFilter only if no filter is already active.
                        // On physical devices, accessibility services register their own
                        // IInputFilter which already activates the filterInputEvent path.
                        // On AVD (no accessibility services), no filter is ever registered,
                        // so filterInputEvent is never called without this.
                        val ims = inputManagerServiceInstance
                        if (ims != null) {
                            val existingFilter = try {
                                XposedHelpers.getObjectField(ims, "mInputFilter")
                            } catch (_: Throwable) { null }
                            if (existingFilter == null) {
                                registerFakeInputFilter(ims, classLoader)
                            }
                        }
                    }
                })

            return
        } catch (t: Throwable) {
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
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext")
                                as android.content.Context
                            GestureManager.initSystemServer(context)
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Failed to initialize GestureManager in start(): ${t.message}")
                        }

                        try {
                            val ptr = XposedHelpers.getLongField(param.thisObject, "mPtr")
                            nativeMethod.invoke(null, ptr, true)
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Legacy InputFilter enable failed: ${t.message}")
                        }
                    }
                })

        } catch (t: Throwable) {
            XposedBridge.log("$TAG: All InputFilter enable approaches failed: ${t.message}")
        }
    }

    /**
     * Register a fake IInputFilter so the native InputDispatcher activates the Java
     * filterInputEvent path. Without a registered IInputFilter, filterInputEvent is
     * never called even when InputFilterEnabled=true (happens on AVD with no
     * accessibility services active).
     *
     * The filter immediately forwards every event via IInputFilterHost.sendInputEvent
     * to avoid blocking dispatch. Our InputManagerService.filterInputEvent hook
     * observes each event for gesture detection before this forwarding happens.
     */
    private fun registerFakeInputFilter(imsInstance: Any, classLoader: ClassLoader) {
        try {
            val iInputFilterClass = XposedHelpers.findClass("android.view.IInputFilter", classLoader)
            val iInputFilterHostClass = XposedHelpers.findClass("android.view.IInputFilterHost", classLoader)
            val sendInputEvent = iInputFilterHostClass.getMethod(
                "sendInputEvent",
                android.view.InputEvent::class.java,
                Int::class.javaPrimitiveType
            )

            var hostRef: Any? = null

            val filterProxy = java.lang.reflect.Proxy.newProxyInstance(
                classLoader,
                arrayOf(iInputFilterClass),
                java.lang.reflect.InvocationHandler { _, method, args ->
                    when (method.name) {
                        "install" -> {
                            hostRef = args?.get(0)
                        }
                        "filterInputEvent" -> {
                            val host = hostRef
                            val event = args?.get(0) as? android.view.InputEvent
                            val policyFlags = args?.get(1) as? Int ?: 0
                            if (host != null && event != null) {
                                try {
                                    sendInputEvent.invoke(host, event, policyFlags)
                                } catch (e: Exception) {
                                    XposedBridge.log("$TAG: sendInputEvent failed: ${e.message}")
                                }
                            }
                        }
                        "asBinder" -> android.os.Binder()
                        else -> null
                    }
                    null
                }
            )

            XposedHelpers.callMethod(imsInstance, "setInputFilter", filterProxy)
        } catch (e: Exception) {
            XposedBridge.log("$TAG: registerFakeInputFilter failed: ${e.message}")
            e.printStackTrace()
        }
    }

}
