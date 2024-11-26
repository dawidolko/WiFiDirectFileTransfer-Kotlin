package github.leavesczy.wifip2p.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.BatteryManager
import android.widget.Toast

class BatteryLevelReceiver(
    private val context: Context,
    private val wifiP2pManager: WifiP2pManager,
    private val wifiP2pChannel: WifiP2pManager.Channel
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale.toFloat()

            if (batteryPct <= 20) {
                disableGPS()

                disableWifiDirect()

                Toast.makeText(context, "Battery low! GPS and communication disabled.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun disableGPS() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val gpsProvider = LocationManager.GPS_PROVIDER
            if (locationManager.isProviderEnabled(gpsProvider)) {
                Toast.makeText(context, "Please disable GPS manually for battery saving.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun disableWifiDirect() {
        wifiP2pManager.removeGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(context, "Wi-Fi Direct disabled due to low battery.", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(reason: Int) {
                Toast.makeText(context, "Failed to disable Wi-Fi Direct.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
