package github.leavesczy.wifip2p

import BaseActivity
import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.motion.widget.Debug.getLocation
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import github.leavesczy.wifip2p.receiver.BatteryLevelReceiver
import github.leavesczy.wifip2p.receiver.FileReceiverActivity
import github.leavesczy.wifip2p.sender.FileSenderActivity

/**
 * @Author: dawidolko
 * @Date: 2024/11/11
 * @Desc:
 */
class MainActivity : BaseActivity() {

    private val requestedPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.CHANGE_NETWORK_STATE,
        Manifest.permission.INTERNET
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestPermissionLaunch = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { it ->
        if (it.all { it.value }) {
            showToast("All permissions granted")
        } else {
            onPermissionDenied()
        }
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "connection_status_channel",
                "Connection Status",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for connection status updates"
            }
            val notificationManager: NotificationManager =
                context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 123

    fun sendNotification(context: Context, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (context is Activity) {
                ActivityCompat.requestPermissions(
                    context,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                Toast.makeText(context, "Notification permission not granted", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "connection_status_channel",
                "Connection Status",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for connection status updates"
            }
            val notificationManager =
                context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "connection_status_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var wifiP2pChannel: WifiP2pManager.Channel
    private lateinit var batteryLevelReceiver: BatteryLevelReceiver

    private val requiredPermissions = mutableListOf<String>().apply {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_WIFI_STATE)
        add(Manifest.permission.CHANGE_WIFI_STATE)
        add(Manifest.permission.ACCESS_NETWORK_STATE)
        add(Manifest.permission.CHANGE_NETWORK_STATE)
        add(Manifest.permission.INTERNET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val grantedPermissions = permissions.filterValues { it }.keys
            val deniedPermissions = permissions.filterValues { !it }.keys

            if (deniedPermissions.isEmpty()) {
                showToast("All permissions granted!")
            } else {
                showToast("Missing permissions: ${deniedPermissions.joinToString(", ")}")
            }
        }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            showToast("All permissions are already granted!")
        } else {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        wifiP2pChannel = wifiP2pManager.initialize(this, mainLooper, null)
        promptEnableLocationServices()

        findViewById<Button>(R.id.btnCheckPermission).setOnClickListener {
            checkAndRequestPermissions()
        }

        findViewById<Button>(R.id.btnSender).setOnClickListener {
            if (areAllPermissionsGranted()) {
                val intent = Intent(this, FileSenderActivity::class.java)
                startActivity(intent)
            } else {
                showToast("Please grant necessary permissions to use Wi-Fi Direct.")
            }
        }

        findViewById<Button>(R.id.btnReceiver).setOnClickListener {
            if (areAllPermissionsGranted()) {
                val intent = Intent(this, FileReceiverActivity::class.java)
                startActivity(intent)
            } else {
                showToast("Please grant necessary permissions to use Wi-Fi Direct.")
            }
        }

        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            try {
                val intent = Intent(this, HistoryActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error opening history", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnShowLocation).setOnClickListener {
            checkLocationPermission()
            getLocation()
        }

        batteryLevelReceiver = BatteryLevelReceiver(this, wifiP2pManager, wifiP2pChannel)
        registerReceiver(batteryLevelReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        requestPermissions()
    }

    private fun initializeWifiDirect() {
        showToast("Wi-Fi Direct initialized successfully.")
    }

    private fun areAllPermissionsGranted(): Boolean {
        return requestedPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Użytkownik przyznał uprawnienia
                getLocation()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>().apply {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }.toTypedArray()

        if (!areAllPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE)
        }
    }


    private lateinit var locationCallback: LocationCallback

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
                priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 1000
                fastestInterval = 500
                numUpdates = 1
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        val locationMessage = "Location: ${location.latitude}, ${location.longitude}"
                        Toast.makeText(this@MainActivity, locationMessage, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Unable to fetch location. Try again later.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun checkLocationServices(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }

    private fun promptEnableLocationServices() {
        if (!checkLocationServices()) {
            Toast.makeText(this, "Please enable location services.", Toast.LENGTH_SHORT).show()
            val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryLevelReceiver)
    }

    private fun onPermissionDenied() {
        showToast("Permissions missing, please grant them first")
    }

    private fun allPermissionGranted(): Boolean {
        requestedPermissions.forEach {
            if (ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

}
