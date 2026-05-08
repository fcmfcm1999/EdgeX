package com.fan.edgex.hook

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import de.robv.android.xposed.XposedBridge

object FlashlightManager {

    @Volatile private var torchOn = false
    @Volatile private var cameraId: String? = null

    fun initialize(context: Context, handler: Handler) {
        try {
            val cm = context.getSystemService(CameraManager::class.java) ?: return
            resolveBackCamera(cm)
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) torchOn = enabled
                }
                override fun onTorchModeUnavailable(id: String) {
                    if (id == cameraId) torchOn = false
                }
            }, handler)
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: FlashlightManager.initialize failed: ${t.message}")
        }
    }

    fun toggle(context: Context) {
        try {
            val cm = context.getSystemService(CameraManager::class.java) ?: return
            val id = cameraId ?: resolveBackCamera(cm) ?: return
            cm.setTorchMode(id, !torchOn)
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: FlashlightManager.toggle failed: ${t.message}")
        }
    }

    private fun resolveBackCamera(cm: CameraManager): String? {
        if (cameraId != null) return cameraId
        for (id in cm.cameraIdList) {
            try {
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    return id
                }
            } catch (_: Throwable) {}
        }
        for (id in cm.cameraIdList) {
            try {
                val hasFlash = cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (hasFlash) {
                    cameraId = id
                    return id
                }
            } catch (_: Throwable) {}
        }
        return null
    }
}
