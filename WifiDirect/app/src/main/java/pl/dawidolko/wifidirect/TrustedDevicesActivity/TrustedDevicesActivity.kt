package pl.dawidolko.wifidirect.TrustedDevicesActivity

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import pl.dawidolko.wifidirect.R
import pl.dawidolko.wifidirect.TrustedDevice
import kotlin.random.Random
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.view.MenuItem
import androidx.core.app.ActivityCompat

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Activity odpowiedzialne za zarządzanie listą zaufanych urządzeń, w tym dodawanie, usuwanie
 * i wyświetlanie urządzeń zapisanych jako zaufane.
 */

class TrustedDevicesActivity : AppCompatActivity() {

    private lateinit var rvTrustedDevices: RecyclerView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var adapter: TrustedDevicesAdapter
    private var trustedDevicesList = mutableListOf<TrustedDevice>()

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: Channel

    companion object {
        private const val PREFERENCES_NAME = "trusted_devices"
        private const val TRUSTED_DEVICES_KEY = "trusted_devices_list"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // Przycisk do pobierania urządzeń
        val btnFetchDevices = findViewById<Button>(R.id.btnFetchDevices)
        btnFetchDevices.setOnClickListener {
            fetchConnectedDevices()
        }

        // Przycisk do dodawania urządzeń
        val btnAddToTrusted = findViewById<Button>(R.id.btnAddToTrusted)
        btnAddToTrusted.setOnClickListener {
            addToTrustedDevices()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchConnectedDevices() {
        wifiP2pManager.requestGroupInfo(channel) { group ->
            if (group != null) {
                Log.d("WiFiDirect", "Group info: $group")
                if (group.clientList.isNotEmpty()) {
                    for (device in group.clientList) {
                        Log.d("WiFiDirect", "Device: ${device.deviceName}, ${device.deviceAddress}")
                        addTrustedDeviceFromWifiDirect(device)
                    }
                } else {
                    Log.d("WiFiDirect", "No connected devices found")
                }
            } else {
                Log.d("WiFiDirect", "No group info available")
            }
        }
    }

    private fun addTrustedDeviceFromWifiDirect(device: WifiP2pDevice) {
        val macAddress = device.deviceAddress
        val deviceName = device.deviceName

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
    }

    private fun addToTrustedDevices() {
        val macAddress = generateRandomMacAddress()
        val deviceName = "Device-${macAddress.takeLast(4)}"

        // Sprawdź, czy urządzenie o tym samym MAC już istnieje
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
        }
    }

    // Obsługa kliknięcia na strzałkę wstecz w ActionBar
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
