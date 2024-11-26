package pl.dawidolko.wifidirect

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import pl.dawidolko.wifidirect.receivers.BatteryBroadcastReceiver

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Główna aktywność aplikacji, zawiera menu i podstawową nawigację między sekcjami aplikacji.
 */

class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var batteryLevelReceiver: BatteryBroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicjalizacja odbiornika
        val wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        val wifiP2pChannel = wifiP2pManager.initialize(this, mainLooper, null)
        batteryLevelReceiver = BatteryBroadcastReceiver(this, wifiP2pManager, wifiP2pChannel)

        // Inicjalizacja przycisków
        val grantPermissionsButton = findViewById<Button>(R.id.grantPermissionsButton)
        val checkLocationButton = findViewById<Button>(R.id.checkLocationButton)
        val wifiDirectButton = findViewById<Button>(R.id.wifiDirectButton)

        // Obsługa przycisku Grant all permissions
        grantPermissionsButton.setOnClickListener {
            checkAndRequestPermissions()
        }

        // Obsługa przycisku Check location
        checkLocationButton.setOnClickListener {
            if (arePermissionsGranted()) {
                getCurrentLocationAndShow()
            } else {
                showToast("Please grant permissions using the button above.")
            }
        }

        // Obsługa przycisku Wi-Fi Direct
        wifiDirectButton.setOnClickListener {
            // Przejście do aktywności Wi-Fi Direct
            val intent = Intent(this, WifiDirectActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        if (arePermissionsGranted()) {
            showToast("Permissions already granted")
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun arePermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getCurrentLocationAndShow() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showToast("Permissions are required to access location.")
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude
                val locationMessage = "Location: $latitude, $longitude"
                showToast(locationMessage)
                openGoogleMaps(latitude, longitude)
            } else {
                showToast("Unable to retrieve location.")
            }
        }
    }

    private fun openGoogleMaps(latitude: Double, longitude: Double) {
        val uri = "geo:$latitude,$longitude"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        intent.setPackage("com.google.android.apps.maps")

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            val webUri = "https://www.google.com/maps?q=$latitude,$longitude"
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri))
            startActivity(webIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showToast("Permissions granted. You can now check location.")
            } else {
                showToast("Permissions denied.")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryLevelReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(batteryLevelReceiver)
    }
}
