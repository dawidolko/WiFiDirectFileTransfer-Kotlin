package pl.dawidolko.wifidirect.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.BatteryManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: BroadcastReceiver odpowiedzialny za monitorowanie poziomu baterii urządzenia.
 * Może być używany do optymalizacji działania aplikacji w zależności od stanu naładowania.
 */

class BatteryBroadcastReceiver(
    private val context: Context,
    private val wifiP2pManager: WifiP2pManager,
    private val wifiP2pChannel: WifiP2pManager.Channel
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = (level * 100 / scale.toFloat())

            if (batteryPct <= 20) {
                disableGPS()
                disableWifiDirect()

                // Informujemy użytkownika o wyłączeniu funkcji
                Toast.makeText(context, "Battery low! GPS and Wi-Fi Direct disabled.", Toast.LENGTH_LONG).show()

                // Wysyłanie powiadomienia o niskim poziomie baterii
                //  sendLowBatteryNotification(context)
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

//    private fun sendLowBatteryNotification(context: Context) {
//        val notificationManager = NotificationManagerCompat.from(context)
//        val notification = NotificationCompat.Builder(context, "battery_channel")
//            .setContentTitle("Battery Low!")
//            .setContentText("GPS and Wi-Fi Direct are disabled to save battery.")
//            .setSmallIcon(android.R.drawable.ic_menu_report_image)  // Zmień ikonę na odpowiednią
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
//            .build()
//
//        notificationManager.notify(1, notification)
//    }
}
