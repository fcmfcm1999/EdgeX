package com.fan.edgex.hook

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object UniversalCopyManager {
    private const val TAG = "EdgeX"
    private const val STATE_ENABLED = 1
    private const val RETRY_COUNT = 3
    private const val RETRY_DELAY_MS = 150L

    enum class CollectStatus {
        FOUND,
        NO_TEXT,
        UNAVAILABLE
    }

    data class TextBlock(
        val text: String,
        val bounds: Rect
    )

    data class CollectResult(
        val status: CollectStatus,
        val blocks: List<TextBlock> = emptyList()
    )

    @Volatile
    private var hooksInstalled = false

    @Volatile
    private var fakeAccessibilityEnabled = false

    @Volatile
    private var service: BridgeAccessibilityService? = null

    fun installHooks(classLoader: ClassLoader) {
        if (hooksInstalled) return
        synchronized(this) {
            if (hooksInstalled) return
            hookClientState(classLoader, "com.android.server.accessibility.AccessibilityUserState", "getClientStateLocked")
            hookClientState(classLoader, "com.android.server.accessibility.AccessibilityManagerService\$UserState", "getClientState")
            hooksInstalled = true
        }
    }

    fun collectAllTexts(context: Context, onResult: (CollectResult) -> Unit) {
        val bridge = getOrCreateService(context)
        if (bridge == null) {
            onResult(CollectResult(CollectStatus.UNAVAILABLE))
            return
        }
        bridge.collectAll(onResult)
    }

    private fun getOrCreateService(context: Context): BridgeAccessibilityService? {
        service?.let { return it }
        return synchronized(this) {
            service?.let { return@synchronized it }
            try {
                BridgeAccessibilityService(context.applicationContext).also {
                    service = it
                }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: Universal copy init failed: ${t.message}")
                null
            }
        }
    }

    private fun hookClientState(classLoader: ClassLoader, className: String, methodName: String) {
        try {
            val targetClass = XposedHelpers.findClass(className, classLoader)
            val hookedAny = targetClass.declaredMethods
                .filter { it.name == methodName }
                .map { method ->
                    runCatching {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!fakeAccessibilityEnabled) return
                                for (index in param.args.indices) {
                                    if (param.args[index] is Boolean) {
                                        param.args[index] = true
                                        break
                                    }
                                }
                            }

                            override fun afterHookedMethod(param: MethodHookParam) {
                                if (!fakeAccessibilityEnabled) return
                                val result = param.result as? Int ?: return
                                if ((result and STATE_ENABLED) == 0) {
                                    param.result = result or STATE_ENABLED
                                }
                            }
                        })
                    }.isSuccess
                }
                .any { it }

            if (hookedAny) {
                XposedBridge.log("$TAG: Universal copy hooked $className#$methodName")
            }
        } catch (_: Throwable) {
        }
    }

    private class BridgeAccessibilityService(context: Context) : AccessibilityService() {
        private val handler = Handler(Looper.getMainLooper())
        private val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        private val managerService = resolveManagerService(accessibilityManager)

        init {
            val connectionId = resolveConnectionId(managerService)
            XposedHelpers.setIntField(this, "mConnectionId", connectionId)
            attachBaseContext(context)
        }

        fun collectAll(onResult: (CollectResult) -> Unit) {
            try {
                if (!accessibilityManager.isEnabled) {
                    setAccessibilityEnabled(true)
                    retryCollect(RETRY_COUNT, true, onResult)
                    return
                }
                finishCollect(queryAllTexts(), false, onResult)
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: Universal copy failed: ${t.message}")
                onResult(CollectResult(CollectStatus.UNAVAILABLE))
            }
        }

        private fun retryCollect(
            attemptsLeft: Int,
            disableAfter: Boolean,
            onResult: (CollectResult) -> Unit
        ) {
            val result = queryAllTexts()
            if (result.rootAvailable || attemptsLeft <= 1) {
                finishCollect(result, disableAfter, onResult)
                return
            }
            handler.postDelayed(
                { retryCollect(attemptsLeft - 1, disableAfter, onResult) },
                RETRY_DELAY_MS
            )
        }

        private fun finishCollect(
            result: QueryResult,
            disableAfter: Boolean,
            onResult: (CollectResult) -> Unit
        ) {
            try {
                val callbackResult = when {
                    !result.rootAvailable -> CollectResult(CollectStatus.UNAVAILABLE)
                    result.items.isEmpty() -> CollectResult(CollectStatus.NO_TEXT)
                    else -> CollectResult(CollectStatus.FOUND, result.items)
                }
                XposedBridge.log("$TAG: Universal copy collected ${result.items.size} text blocks, status=${callbackResult.status}")
                onResult(callbackResult)
            } finally {
                if (disableAfter) {
                    setAccessibilityEnabled(false)
                }
            }
        }

        private fun queryAllTexts(): QueryResult {
            val root = try {
                getRootInActiveWindow()
            } catch (_: Throwable) {
                null
            } ?: return QueryResult(rootAvailable = false, items = emptyList())

            return QueryResult(
                rootAvailable = true,
                items = PageTextCollector.collectAll(root)
            )
        }

        private fun setAccessibilityEnabled(enabled: Boolean) {
            fakeAccessibilityEnabled = enabled
            updateUiAutomationFlags(enabled)
            try {
                val currentUserState = XposedHelpers.callMethod(managerService, "getCurrentUserState")
                XposedHelpers.callMethod(managerService, "scheduleUpdateClientsIfNeeded", currentUserState)
            } catch (_: Throwable) {
                try {
                    val lock = XposedHelpers.getObjectField(managerService, "mLock")
                    synchronized(lock) {
                        val currentUserState =
                            XposedHelpers.callMethod(managerService, "getCurrentUserStateLocked")
                        XposedHelpers.callMethod(
                            managerService,
                            "scheduleUpdateClientsIfNeededLocked",
                            currentUserState
                        )
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG: Universal copy state update failed: ${t.message}")
                }
            }
        }

        private fun updateUiAutomationFlags(enabled: Boolean) {
            try {
                val uiAutomationManager = XposedHelpers.getObjectField(managerService, "mUiAutomationManager")
                val flagsField = XposedHelpers.findField(uiAutomationManager.javaClass, "mUiAutomationFlags")
                val currentFlags = flagsField.getInt(uiAutomationManager)
                val updatedFlags = if (enabled) {
                    currentFlags or 0x2
                } else {
                    currentFlags and 0x2.inv()
                }
                flagsField.setInt(uiAutomationManager, updatedFlags)
            } catch (_: Throwable) {
            }
        }

        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

        override fun onInterrupt() = Unit

        companion object {
            private fun resolveManagerService(accessibilityManager: AccessibilityManager): Any {
                return try {
                    XposedHelpers.getObjectField(accessibilityManager, "mService")
                } catch (_: Throwable) {
                    val lock = XposedHelpers.getObjectField(accessibilityManager, "mLock")
                    synchronized(lock) {
                        XposedHelpers.callMethod(accessibilityManager, "getServiceLocked")
                    }
                }
            }

            private fun resolveConnectionId(managerService: Any): Int {
                val bridge = try {
                    XposedHelpers.callMethod(managerService, "getInteractionBridge")
                } catch (_: Throwable) {
                    val lock = XposedHelpers.getObjectField(managerService, "mLock")
                    synchronized(lock) {
                        XposedHelpers.callMethod(managerService, "getInteractionBridgeLocked")
                    }
                }
                return XposedHelpers.getIntField(bridge, "mConnectionId")
            }
        }
    }

    private data class QueryResult(
        val rootAvailable: Boolean,
        val items: List<TextBlock>
    )

    /**
     * Traverses the entire accessibility tree and collects all visible text,
     * ordered top-to-bottom by screen position.
     */
    private object PageTextCollector {
        private val tempRect = Rect()

        fun collectAll(root: AccessibilityNodeInfo): List<TextBlock> {
            val items = mutableListOf<TextItem>()
            traverse(root, items)
            items.sortWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            return deduplicate(items).map { TextBlock(it.text, it.bounds) }
        }

        private fun traverse(node: AccessibilityNodeInfo, items: MutableList<TextItem>) {
            if (!node.isVisibleToUser) return

            val className = node.className?.toString().orEmpty()
            if (className.contains("Image", ignoreCase = true)) return

            // Try children first — prefer leaf-level text
            var childrenHadText = false
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val sizeBefore = items.size
                traverse(child, items)
                if (items.size > sizeBefore) childrenHadText = true
            }

            // Only add this node's text if no children contributed text
            if (childrenHadText) return

            val text = normalizeText(node.text) ?: normalizeText(node.contentDescription) ?: return
            node.getBoundsInScreen(tempRect)
            items += TextItem(text, Rect(tempRect))
        }

        private fun deduplicate(items: List<TextItem>): List<TextItem> {
            if (items.size <= 1) return items
            val seen = LinkedHashSet<String>()
            val result = mutableListOf<TextItem>()
            for (item in items) {
                if (seen.add(item.text)) {
                    result += item
                }
            }
            return result
        }

        private fun normalizeText(text: CharSequence?): String? {
            if (text.isNullOrEmpty()) return null
            val str = if (text.length > 65536) text.subSequence(0, 65536).toString() else text.toString()
            val trimmed = str.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.length == 1) {
                val c = trimmed[0]
                if (!((c > ' ' && c <= '~') || c.code > 160)) return null
            }
            return trimmed
        }

        private data class TextItem(val text: String, val bounds: Rect)
    }
}
