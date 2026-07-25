package com.bb8.app.data

import android.content.Context
import com.bb8.app.ble.ScannedDevice

class Bb8Preferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    var autoReconnectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECONNECT, value).apply()

    fun saveLastDevice(device: ScannedDevice) {
        prefs.edit()
            .putString(KEY_LAST_DEVICE_NAME, device.name)
            .putString(KEY_LAST_DEVICE_ADDRESS, device.address)
            .apply()
    }

    fun lastDevice(): ScannedDevice? {
        val name = prefs.getString(KEY_LAST_DEVICE_NAME, null) ?: return null
        val address = prefs.getString(KEY_LAST_DEVICE_ADDRESS, null) ?: return null
        return ScannedDevice(name = name, address = address)
    }

    fun clearLastDevice() {
        prefs.edit()
            .remove(KEY_LAST_DEVICE_NAME)
            .remove(KEY_LAST_DEVICE_ADDRESS)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "bb8_prefs"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_LAST_DEVICE_NAME = "last_device_name"
        private const val KEY_LAST_DEVICE_ADDRESS = "last_device_address"
    }
}
