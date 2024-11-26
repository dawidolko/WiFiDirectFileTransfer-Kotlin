package github.leavesczy.wifip2p.utils

import android.net.wifi.p2p.WifiP2pDevice

/**
 * @Author: dawidolko
 * @Desc:
 */
object WifiP2pUtils {

    fun getDeviceStatus(deviceStatus: Int): String {
        return when (deviceStatus) {
            WifiP2pDevice.AVAILABLE -> "Available"
            WifiP2pDevice.INVITED -> "Invited"
            WifiP2pDevice.CONNECTED -> "Connected"
            WifiP2pDevice.FAILED -> "Failed"
            WifiP2pDevice.UNAVAILABLE -> "Unavailable"
            else -> "Unknown"
        }
    }

}
