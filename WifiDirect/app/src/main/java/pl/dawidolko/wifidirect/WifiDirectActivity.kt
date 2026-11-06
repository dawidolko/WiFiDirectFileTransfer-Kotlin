package pl.dawidolko.wifidirect

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import pl.dawidolko.wifidirect.FileActivity.FileReceiverActivity
import pl.dawidolko.wifidirect.FileActivity.FileSenderActivity
import pl.dawidolko.wifidirect.HistoryActivity.HistoryActivity
import pl.dawidolko.wifidirect.TrustedDevicesActivity.TrustedDevicesActivity

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Central activity for Wi-Fi Direct features. Handles navigation to the
 * sending, receiving, trusted devices and history modules.
 */

class WifiDirectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_direct)

        /*
         * Enable the back arrow in the ActionBar
         */
        supportActionBar?.apply {
            title = "Menu Wifi Direct"
            setDisplayHomeAsUpEnabled(true)
        }

        val btnSender = findViewById<Button>(R.id.btnSender)
        val btnReceiver = findViewById<Button>(R.id.btnReceiver)
        val btnWifiDirect = findViewById<Button>(R.id.btnWifiDirect)
        val btnTrustedDevices = findViewById<Button>(R.id.btnTrustedDevices)
        val btnHistoryFile = findViewById<Button>(R.id.btnHistoryFile)

        /*
         * Handle the "Send File" button - open FileSenderActivity
         */
        btnSender.setOnClickListener {
            val intent = Intent(this, FileSenderActivity::class.java)
            startActivity(intent)
        }

        /*
         * Handle the "Receive File" button - open FileReceiverActivity
         */
        btnReceiver.setOnClickListener {
            val intent = Intent(this, FileReceiverActivity::class.java)
            startActivity(intent)
        }

        /*
         * Handle the "Wi-Fi Direct Settings" button - open the Wi-Fi Direct settings
         */
        btnWifiDirect.setOnClickListener {
            /*
             * You can also use Settings.ACTION_WIFI_SETTINGS
             */
            val intent = Intent(Settings.ACTION_WIFI_IP_SETTINGS)
            startActivity(intent)
        }

        /*
         * Handle the "Trusted Devices" button - open the trusted devices view
         */
        btnTrustedDevices.setOnClickListener {
            val intent = Intent(this, TrustedDevicesActivity::class.java)
            startActivity(intent)
        }

        /*
         * Handle the "History Files" button - open the file history view
         */
        btnHistoryFile.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

    }

    /*
     * Handle a click on the back arrow in the ActionBar
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
