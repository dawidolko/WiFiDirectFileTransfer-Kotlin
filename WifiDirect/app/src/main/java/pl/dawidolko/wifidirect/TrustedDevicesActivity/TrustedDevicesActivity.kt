package pl.dawidolko.wifidirect.TrustedDevicesActivity

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import pl.dawidolko.wifidirect.R
import pl.dawidolko.wifidirect.TrustedDevice
import kotlin.random.Random

class TrustedDevicesActivity : AppCompatActivity() {

    private lateinit var rvTrustedDevices: RecyclerView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var adapter: TrustedDevicesAdapter
    private var trustedDevicesList = mutableListOf<TrustedDevice>()

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel

    companion object {
        private const val PREFERENCES_NAME = "trusted_devices"
        private const val TRUSTED_DEVICES_KEY = "trusted_devices_list"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val NEARBY_DEVICES_PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TrustedDevicesActivity", "onCreate called")
        setContentView(R.layout.activity_trusted_devices)

        supportActionBar?.apply {
            title = "Trusted Devices"
            setDisplayHomeAsUpEnabled(true)
        }

        rvTrustedDevices = findViewById(R.id.rvTrustedDevices)
        rvTrustedDevices.layoutManager = LinearLayoutManager(this)
        adapter = TrustedDevicesAdapter(trustedDevicesList)
        rvTrustedDevices.adapter = adapter

        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)

        loadTrustedDevices()
        updateEmptyMessageVisibility()

        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)

        val btnFetchDevices = findViewById<Button>(R.id.btnFetchDevices)
        btnFetchDevices.setOnClickListener {
            Log.d("TrustedDevicesActivity", "Fetch devices button clicked. Checking permissions...")
            if (hasRequiredPermissions()) {
                Log.d("TrustedDevicesActivity", "All required permissions granted. Fetching devices...")
                fetchConnectedDevices()
            } else {
                Log.d("TrustedDevicesActivity", "Missing permissions. Requesting now...")
                requestRequiredPermissions()
            }
        }

        val btnAddToTrusted = findViewById<Button>(R.id.btnAddToTrusted)
        btnAddToTrusted.setOnClickListener {
            addToTrustedDevices()
        }

        // Sprawdź uprawnienia na starcie
        if (!hasRequiredPermissions()) {
            Log.d("TrustedDevicesActivity", "Not all permissions granted on start. Requesting...")
            requestRequiredPermissions()
        } else {
            Log.d("TrustedDevicesActivity", "All permissions granted on start.")
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("TrustedDevicesActivity", "onStart: hasRequiredPermissions()=${hasRequiredPermissions()}")
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNearbyDevices = ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
            Log.d("TrustedDevicesActivity", "Checking permissions on Android 13+: FineLocation=$hasFineLocation, NearbyWifi=$hasNearbyDevices")
            return hasFineLocation && hasNearbyDevices
        }
        Log.d("TrustedDevicesActivity", "Checking permissions on pre-Android 13: FineLocation=$hasFineLocation")
        return hasFineLocation
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 i wyżej - wymagamy NEARBY_WIFI_DEVICES
            val permissionsToRequest = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (permissionsToRequest.isNotEmpty()) {
                Log.d("TrustedDevicesActivity", "Requesting permissions: $permissionsToRequest")
                ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), NEARBY_DEVICES_PERMISSION_REQUEST_CODE)
            } else {
                Log.d("TrustedDevicesActivity", "All permissions already granted for Android 13+.")
            }
        } else {
            // Starsze wersje Androida - wystarczy ACCESS_FINE_LOCATION
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.d("TrustedDevicesActivity", "Requesting ACCESS_FINE_LOCATION for pre-Android 13.")
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            } else {
                Log.d("TrustedDevicesActivity", "ACCESS_FINE_LOCATION already granted.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchConnectedDevices() {
        Log.d("TrustedDevicesActivity", "fetchConnectedDevices() called. Requesting group info...")
        wifiP2pManager.requestGroupInfo(channel) { group ->
            if (group != null) {
                Log.d("WiFiDirect", "Group info: $group")

                // Sprawdź, czy to urządzenie jest GO
                if (group.isGroupOwner) {
                    // Jeśli jest GO, używamy listy klientów
                    if (group.clientList.isNotEmpty()) {
                        Log.d("WiFiDirect", "Found ${group.clientList.size} connected devices.")
                        for (device in group.clientList) {
                            Log.d("WiFiDirect", "Device: ${device.deviceName}, ${device.deviceAddress}")
                            addTrustedDeviceFromWifiDirect(device)
                        }
                    } else {
                        Log.d("WiFiDirect", "No connected devices found in group.")
                        Toast.makeText(this, "No connected devices found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Urządzenie jest klientem. Klient nie widzi siebie w clientList.
                    // Właściciel grupy jest widoczny jako "group.owner".
                    // Jeśli jestesmy w grupie, to na pewno jest jakiś właściciel.
                    val owner = group.owner
                    if (owner != null) {
                        Log.d("WiFiDirect", "Device is a client. Adding group owner as connected device.")
                        Log.d("WiFiDirect", "Owner: ${owner.deviceName}, ${owner.deviceAddress}")
                        // Dodaj właściciela jakoby był "podłączonym urządzeniem"
                        addTrustedDeviceFromWifiDirect(owner)
                    } else {
                        Log.d("WiFiDirect", "No owner info found, no connected devices.")
                        Toast.makeText(this, "No connected devices found", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.d("WiFiDirect", "No group info available")
                Toast.makeText(this, "No group info available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addTrustedDeviceFromWifiDirect(device: WifiP2pDevice) {
        Log.d("WiFiDirect", "addTrustedDeviceFromWifiDirect called with deviceName=${device.deviceName}, deviceAddress=${device.deviceAddress}")
        val macAddress = if (device.deviceAddress.isNullOrEmpty() || device.deviceAddress == "02:00:00:00:00:00") {
            Log.w("WiFiDirect", "Device has a placeholder MAC address. Using generated MAC.")
            "MAC-unknown-${device.deviceName.hashCode()}"
        } else {
            device.deviceAddress
        }

        val deviceName = if (device.deviceName.isNullOrEmpty()) {
            Log.w("WiFiDirect", "Device has no name. Generating name.")
            "UnknownDevice-${macAddress.takeLast(4)}"
        } else {
            device.deviceName
        }

        if (trustedDevicesList.any { it.macAddress == macAddress }) {
            Log.d("WiFiDirect", "Device $deviceName ($macAddress) already in list")
            Toast.makeText(this, "Device already added: $deviceName ($macAddress)", Toast.LENGTH_SHORT).show()
            return
        }

        val trustedDevice = TrustedDevice(deviceName, macAddress)
        trustedDevicesList.add(trustedDevice)
        adapter.addDevice(trustedDevice)

        updateEmptyMessageVisibility()
        saveTrustedDevices()

        Toast.makeText(this, "Device added: $deviceName ($macAddress)", Toast.LENGTH_SHORT).show()
        Log.d("WiFiDirect", "Device added: $deviceName ($macAddress)")
    }

    private fun addToTrustedDevices() {
        val macAddress = generateRandomMacAddress()
        val deviceName = "Device-${macAddress.takeLast(4)}"

        if (trustedDevicesList.any { it.macAddress == macAddress }) {
            Toast.makeText(this, "Device already added: $deviceName ($macAddress)", Toast.LENGTH_SHORT).show()
            return
        }

        val trustedDevice = TrustedDevice(deviceName, macAddress)
        trustedDevicesList.add(trustedDevice)
        adapter.addDevice(trustedDevice)

        updateEmptyMessageVisibility()
        saveTrustedDevices()

        Toast.makeText(this, "Device added: $deviceName ($macAddress)", Toast.LENGTH_SHORT).show()
        Log.d("TrustedDevicesActivity", "Manually added device: $deviceName ($macAddress)")
    }

    private fun generateRandomMacAddress(): String {
        val random = Random
        return "02:${String.format("%02X", random.nextInt(256))}:${String.format("%02X", random.nextInt(256))}:${String.format("%02X", random.nextInt(256))}:${String.format("%02X", random.nextInt(256))}:${String.format("%02X", random.nextInt(256))}"
    }

    private fun updateEmptyMessageVisibility() {
        tvEmptyMessage.visibility = if (trustedDevicesList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun saveTrustedDevices() {
        val sharedPreferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val trustedDevicesJson = gson.toJson(trustedDevicesList)
        editor.putString(TRUSTED_DEVICES_KEY, trustedDevicesJson)
        editor.apply()
    }

    private fun loadTrustedDevices() {
        val sharedPreferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val json = sharedPreferences.getString(TRUSTED_DEVICES_KEY, "[]")
        if (json != null) {
            val gson = Gson()
            val type = object : TypeToken<MutableList<TrustedDevice>>() {}.type
            trustedDevicesList = gson.fromJson(json, type)
            adapter = TrustedDevicesAdapter(trustedDevicesList)
            rvTrustedDevices.adapter = adapter
            adapter.notifyDataSetChanged()
            Log.d("TrustedDevicesActivity", "Loaded ${trustedDevicesList.size} trusted devices from preferences.")
        } else {
            Log.d("TrustedDevicesActivity", "No trusted devices found in preferences.")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d("TrustedDevicesActivity", "onRequestPermissionsResult: code=$requestCode, permissions=${permissions.joinToString()}, results=${grantResults.joinToString()}")
        if ((requestCode == LOCATION_PERMISSION_REQUEST_CODE || requestCode == NEARBY_DEVICES_PERMISSION_REQUEST_CODE) && grantResults.isNotEmpty()) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show()
                Log.d("TrustedDevicesActivity", "All requested permissions granted.")
            } else {
                Toast.makeText(this, "Required permissions not granted.", Toast.LENGTH_SHORT).show()
                Log.w("TrustedDevicesActivity", "Not all requested permissions were granted.")
            }
        }
    }
}
