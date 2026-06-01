package com.fan.edgex.hook

import android.view.MotionEvent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object ScrollHook {
    private const val TAG = "EdgeX.ScrollHook"

    fun install() {
        try {
            // Hook ScrollView.onGenericMotionEvent
            val scrollViewClass = XposedHelpers.findClass("android.widget.ScrollView", null)
            XposedHelpers.findAndHookMethod(
                scrollViewClass,
                "onGenericMotionEvent",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val event = param.args[0] as MotionEvent
                        val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                        if (axisValue == 100000.0f || axisValue == -100000.0f) {
                            val scrollView = param.thisObject
                            if (axisValue == 100000.0f) {
                                XposedHelpers.callMethod(scrollView, "smoothScrollTo", 0, 0)
                            } else {
                                val range = XposedHelpers.callMethod(scrollView, "computeVerticalScrollRange") as Int
                                XposedHelpers.callMethod(scrollView, "smoothScrollTo", 0, range)
                            }
                            param.result = true
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ScrollView hook failed: ${t.message}")
        }

        try {
            // Hook AbsListView.onGenericMotionEvent
            val absListViewClass = XposedHelpers.findClass("android.widget.AbsListView", null)
            val gridViewClass = XposedHelpers.findClass("android.widget.GridView", null)
            XposedHelpers.findAndHookMethod(
                absListViewClass,
                "onGenericMotionEvent",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val event = param.args[0] as MotionEvent
                        val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                        if (axisValue == 100000.0f || axisValue == -100000.0f) {
                            val absListView = param.thisObject
                            if (axisValue != 100000.0f) {
                                val count = XposedHelpers.callMethod(absListView, "getCount") as Int
                                XposedHelpers.callMethod(absListView, "smoothScrollToPosition", count - 1)
                            } else if (gridViewClass.isInstance(absListView)) {
                                XposedHelpers.callMethod(absListView, "smoothScrollToPositionFromTop", 0, 0)
                            } else {
                                XposedHelpers.callMethod(absListView, "smoothScrollToPosition", 0)
                            }
                            param.result = true
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: AbsListView hook failed: ${t.message}")
        }

        try {
            // Hook WebView.onGenericMotionEvent
            val webViewClass = XposedHelpers.findClass("android.webkit.WebView", null)
            XposedHelpers.findAndHookMethod(
                webViewClass,
                "onGenericMotionEvent",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    private fun computeWebViewScroll(delta: Int): Int {
                        if (delta != 0) {
                            return (delta * 200) / Math.sqrt(delta.toDouble()).toInt()
                        }
                        return 0
                    }

                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val event = param.args[0] as MotionEvent
                        val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                        if (axisValue == 100000.0f || axisValue == -100000.0f) {
                            val webView = param.thisObject
                            val offset = XposedHelpers.callMethod(webView, "computeVerticalScrollOffset") as Int
                            val range = if (axisValue == 100000.0f) {
                                -computeWebViewScroll(offset)
                            } else {
                                val totalRange = XposedHelpers.callMethod(webView, "computeVerticalScrollRange") as Int
                                computeWebViewScroll(totalRange - offset)
                            }
                            if (range != 0) {
                                XposedHelpers.callMethod(webView, "flingScroll", 0, range)
                            }
                            param.result = true
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: WebView hook failed: ${t.message}")
        }
    }
}
