package com.fan.edgex.hook

/**
 * Legacy entry point — delegates to MainHook.
 * Kept for backward compatibility with any references.
 */
class XposedInit : de.robv.android.xposed.IXposedHookLoadPackage {
    private val delegate = MainHook()

    override fun handleLoadPackage(lpparam: de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam) {
        delegate.handleLoadPackage(lpparam)
    }
}
