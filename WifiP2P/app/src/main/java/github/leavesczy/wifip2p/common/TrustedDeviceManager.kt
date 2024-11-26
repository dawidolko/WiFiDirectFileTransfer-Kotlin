// common/TrustedDeviceManager.kt
package github.leavesczy.wifip2p

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object TrustedDeviceManager {
    private const val PREF_NAME = "trusted_devices"
    private const val KEY_DEVICES = "trusted_devices_list"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun addTrustedDevice(context: Context, deviceAddress: String) {
        val prefs = getPrefs(context)
        val trustedDevices = getTrustedDevices(context).toMutableSet()
        trustedDevices.add(deviceAddress)
        prefs.edit().putStringSet(KEY_DEVICES, trustedDevices).apply()
        Log.d("TrustedDeviceManager", "Device added to trusted list: $deviceAddress")
    }

    fun removeTrustedDevice(context: Context, deviceAddress: String) {
        val prefs = getPrefs(context)
        val trustedDevices = getTrustedDevices(context).toMutableSet()
        trustedDevices.remove(deviceAddress)
        prefs.edit().putStringSet(KEY_DEVICES, trustedDevices).apply()
    }

    fun isTrustedDevice(context: Context, deviceAddress: String): Boolean {
        return getTrustedDevices(context).contains(deviceAddress)
    }

    private fun getTrustedDevices(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_DEVICES, emptySet()) ?: emptySet()
    }

    fun getTrustedDevicesAsString(context: Context): String {
        val trustedDevices = getTrustedDevices(context)
        return if (trustedDevices.isNotEmpty()) {
            trustedDevices.joinToString(separator = "\n")
        } else {
            "No trusted devices"
        }
    }
}
