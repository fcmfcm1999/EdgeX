package com.fan.edgex.hook

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.provider.Settings
import android.telephony.TelephonyManager

internal object ConditionEvaluator {

    fun evaluate(conditionCode: String, context: Context): Boolean = try {
        when (conditionCode) {
            "auto_brightness" -> isAutoBrightnessOn(context)
            "auto_rotate" -> isAutoRotateOn(context)
            "wifi_enabled" -> isWifiEnabled(context)
            "mobile_data" -> isMobileDataEnabled(context)
            "location" -> isLocationEnabled(context)
            "bluetooth" -> isBluetoothEnabled(context)
            "nfc" -> isNfcEnabled(context)
            "power_connected" -> isPowerConnected(context)
            "wifi_connected" -> isWifiConnected(context)
            "network_connected" -> isNetworkConnected(context)
            "media_playing" -> isMediaPlaying(context)
            "screen_portrait" -> isPortrait(context)
            "screen_landscape" -> isLandscape(context)
            else -> false
        }
    } catch (_: Throwable) {
        false
    }

    private fun isAutoBrightnessOn(context: Context) =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    private fun isAutoRotateOn(context: Context) =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun isWifiEnabled(context: Context): Boolean {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return wm.isWifiEnabled
    }

    private fun isMobileDataEnabled(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        return tm.dataState == TelephonyManager.DATA_CONNECTED
    }

    private fun isLocationEnabled(context: Context) =
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) !=
            Settings.Secure.LOCATION_MODE_OFF

    private fun isBluetoothEnabled(context: Context): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        return bm.adapter?.state == BluetoothAdapter.STATE_ON
    }

    private fun isNfcEnabled(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        return adapter.isEnabled
    }

    private fun isPowerConnected(context: Context): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return false
        return bm.isCharging
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isMediaPlaying(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return am.isMusicActive
    }

    private fun isPortrait(context: Context) =
        context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun isLandscape(context: Context) =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
