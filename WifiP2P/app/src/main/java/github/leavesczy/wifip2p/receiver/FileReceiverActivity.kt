package github.leavesczy.wifip2p.receiver

import BaseActivity
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import github.leavesczy.wifip2p.DirectActionListener
import github.leavesczy.wifip2p.DirectBroadcastReceiver
import github.leavesczy.wifip2p.R
import github.leavesczy.wifip2p.common.FileTransferViewState
import kotlinx.coroutines.launch

class FileReceiverActivity : BaseActivity() {

    private lateinit var btnCreateGroup: Button
    private lateinit var btnRemoveGroup: Button
    private lateinit var btnStartReceive: Button
    private lateinit var btnPauseResume: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercentage: TextView
    private lateinit var ivImage: ImageView
    private lateinit var tvLog: TextView

    private var isTransferPaused = false
    private val fileReceiverViewModel by viewModels<FileReceiverViewModel>()
    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var wifiP2pChannel: WifiP2pManager.Channel
    private var broadcastReceiver: BroadcastReceiver? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_receiver)

        // Inicjalizacja elementów UI
        btnCreateGroup = findViewById(R.id.btnCreateGroup)
        btnRemoveGroup = findViewById(R.id.btnRemoveGroup)
        btnStartReceive = findViewById(R.id.btnStartReceive)
        btnPauseResume = findViewById(R.id.btnPauseResume)
        progressBar = findViewById(R.id.progressBar)
        tvProgressPercentage = findViewById(R.id.tvProgressPercentage)
        ivImage = findViewById(R.id.ivImage)
        tvLog = findViewById(R.id.tvLog)

        // Ustawienia przycisków
        btnCreateGroup.setOnClickListener { createGroup() }
        btnRemoveGroup.setOnClickListener { removeGroup() }
        btnStartReceive.setOnClickListener { fileReceiverViewModel.startListener() }

        btnPauseResume.setOnClickListener {
            if (isTransferPaused) {
                resumeReceiving()
                btnPauseResume.text = "Pause"
            } else {
                pauseReceiving()
                btnPauseResume.text = "Resume"
            }
            isTransferPaused = !isTransferPaused
        }

        // Inicjalizacja urządzenia i wydarzeń
        initDevice()
        initEvent()

        // Inicjalizacja Wi-Fi P2P Manager
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        wifiP2pChannel = wifiP2pManager.initialize(this, mainLooper, null)

        // Sprawdzanie istniejącej grupy
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED)
        ) {
            wifiP2pManager.requestGroupInfo(wifiP2pChannel) { group ->
                if (group != null) {
                    log("Existing group found:")
                    log("SSID: ${group.networkName}")
                    log("Passphrase: ${group.passphrase}")
                    log("Owner: ${group.owner.deviceName} (${group.owner.deviceAddress})")
                } else {
                    log("No existing group found.")
                }
            }
        } else {
            log("Missing required permissions for group info.")
        }

        // Ustawienia ActionBar
        supportActionBar?.apply {
            title = "File Receiver"
            setDisplayHomeAsUpEnabled(true)
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

    private fun initEvent() {
        lifecycleScope.launch {
            fileReceiverViewModel.fileTransferViewState.collect { state ->
                when (state) {
                    is FileTransferViewState.Progress -> {
                        progressBar.visibility = View.VISIBLE
                        tvProgressPercentage.visibility = View.VISIBLE
                        btnPauseResume.visibility = View.VISIBLE

                        progressBar.progress = state.progress
                        tvProgressPercentage.text = "${state.progress}%"
                    }
                    is FileTransferViewState.Success -> {
                        progressBar.visibility = View.GONE
                        tvProgressPercentage.visibility = View.GONE
                        btnPauseResume.visibility = View.GONE
                        ivImage.load(state.file)
                        log("File received successfully at: ${state.file.absolutePath}")
                        showToast("File received: ${state.file.name}")
                    }
                    is FileTransferViewState.Failed -> {
                        progressBar.visibility = View.GONE
                        tvProgressPercentage.visibility = View.GONE
                        btnPauseResume.visibility = View.GONE
                        log("File transfer failed: ${state.throwable.message}")
                    }
                    else -> { /* Obsługa innych stanów */ }
                }
            }
        }

        lifecycleScope.launch {
            fileReceiverViewModel.log.collect { logMessage ->
                tvLog.append("$logMessage\n")
            }
        }
    }


    private fun initDevice() {
        val manager = getSystemService(WIFI_P2P_SERVICE) as? WifiP2pManager
        if (manager == null) {
            finish()
            return
        }
        wifiP2pManager = manager
        wifiP2pChannel = wifiP2pManager.initialize(this, mainLooper, directActionListener)
        broadcastReceiver = DirectBroadcastReceiver(
            wifiP2pManager = wifiP2pManager,
            wifiP2pChannel = wifiP2pChannel,
            directActionListener = directActionListener
        )
        registerReceiver(broadcastReceiver, DirectBroadcastReceiver.getIntentFilter())
    }

    private fun pauseReceiving() = fileReceiverViewModel.pauseReceiving()

    private fun resumeReceiving() = fileReceiverViewModel.resumeReceiving()

    override fun onDestroy() {
        super.onDestroy()
        broadcastReceiver?.let { unregisterReceiver(it) }
        removeGroup()
    }

    private val directActionListener = object : DirectActionListener {
        override fun wifiP2pEnabled(enabled: Boolean) {
            log("Wi-Fi P2P enabled: $enabled")
        }

        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            log("Connection info available: group formed=${wifiP2pInfo.groupFormed}")
        }

        override fun onDisconnection() {
            log("Disconnected")
        }

        override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
            log("Self device available: $wifiP2pDevice")
        }

        override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice>) {
            log("Peers available: ${wifiP2pDeviceList.size}")
        }

        override fun onChannelDisconnected() {
            log("Channel disconnected")
        }
    }

    @SuppressLint("MissingPermission")
    private fun createGroup() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (!hasPermissions(requiredPermissions)) {
            ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
            showToast("Please grant the required permissions")
            return
        }

        try {
            wifiP2pManager.createGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    showToast("Group created successfully")
                    log("Wi-Fi P2P group created successfully")
                    // Pobierz szczegóły grupy po jej utworzeniu
                    wifiP2pManager.requestGroupInfo(wifiP2pChannel) { group ->
                        if (group != null) {
                            log("Group details:")
                            log("SSID: ${group.networkName}")
                            log("Passphrase: ${group.passphrase}")
                            log("Owner: ${group.owner.deviceName} (${group.owner.deviceAddress})")
                        } else {
                            log("Group created but no group info available.")
                        }
                    }
                }

                override fun onFailure(reason: Int) {
                    val errorMessage = when (reason) {
                        WifiP2pManager.BUSY -> "Wi-Fi P2P is busy"
                        WifiP2pManager.ERROR -> "Internal error occurred"
                        WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported on this device"
                        else -> "Unknown error: $reason"
                    }
                    showToast("Failed to create group: $errorMessage")
                    log("Failed to create group: $errorMessage")
                }
            })
        } catch (e: Exception) {
            log("Error while creating group: ${e.message}")
            showToast("Error while creating group: ${e.message}")
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private fun removeGroup() {
        wifiP2pManager.removeGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                showToast("Group removed successfully")
                log("Wi-Fi P2P group removed successfully")
            }

            override fun onFailure(reason: Int) {
                val errorMessage = when (reason) {
                    WifiP2pManager.BUSY -> "Manager is busy"
                    WifiP2pManager.ERROR -> "Internal error"
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported"
                    else -> "Unknown error: $reason"
                }
                showToast("Failed to remove group: $errorMessage")
                log("Failed to remove group: $errorMessage")
            }
        })
    }

    private fun log(message: String) {
        tvLog.append(message)
        tvLog.append("\n\n")
    }
}
