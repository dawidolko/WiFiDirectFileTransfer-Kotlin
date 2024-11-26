package github.leavesczy.wifip2p.sender

import BaseActivity
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import github.leavesczy.wifip2p.DeviceAdapter
import github.leavesczy.wifip2p.DirectActionListener
import github.leavesczy.wifip2p.DirectBroadcastReceiver
import github.leavesczy.wifip2p.HistoryActivity
import github.leavesczy.wifip2p.OnItemClickListener
import github.leavesczy.wifip2p.R
import github.leavesczy.wifip2p.common.FileTransferViewState
import github.leavesczy.wifip2p.utils.WifiP2pUtils
import kotlinx.coroutines.launch
import github.leavesczy.wifip2p.TrustedDeviceManager


/**
 * @Author: dawidolko
 * @Date: 2024/11/11
 * @Desc:
 */
@SuppressLint("NotifyDataSetChanged")
class FileSenderActivity : BaseActivity() {

    private val tvDeviceState by lazy {
        findViewById<TextView>(R.id.tvDeviceState)
    }

    private val tvSelectedFileName by lazy {
        findViewById<TextView>(R.id.tvSelectedFileName)
    }


    private val tvConnectionStatus by lazy {
        findViewById<TextView>(R.id.tvConnectionStatus)
    }

    private val btnDisconnect by lazy {
        findViewById<Button>(R.id.btnDisconnect)
    }

    private val btnChooseFile by lazy {
        findViewById<Button>(R.id.btnChooseFile)
    }

    private val rvDeviceList by lazy {
        findViewById<RecyclerView>(R.id.rvDeviceList)
    }

    private val tvLog by lazy {
        findViewById<TextView>(R.id.tvLog)
    }

    private val btnDirectDiscover by lazy {
        findViewById<Button>(R.id.btnDirectDiscover)
    }

    private val btnPauseResume by lazy {
        findViewById<Button>(R.id.btnPauseResume)
    }

    private val btnConnect by lazy {
        findViewById<Button>(R.id.btnConnect)
    }

    private val btnSendFile by lazy {
        findViewById<Button>(R.id.btnSendFile)
    }


    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercentage: TextView

    private var isTransferPaused = false

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private val fileSenderViewModel by viewModels<FileSenderViewModel>()

    private lateinit var tvTrustedDevices: TextView

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { imageUri ->
            if (imageUri != null) {
                currentFileUri = imageUri

                val documentFile = DocumentFile.fromSingleUri(this, imageUri)
                val fileName = documentFile?.name ?: "Unknown File"

                tvSelectedFileName.text = "Selected File: $fileName"
                log("File selected: $fileName")
            } else {
                showToast("File selection canceled")
            }
        }

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES, // Android 12+
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, // Android <12
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
    }

    private val wifiP2pDeviceList = mutableListOf<WifiP2pDevice>()

    private val deviceAdapter = DeviceAdapter(wifiP2pDeviceList)

    private var currentFileUri: Uri? = null

    private var broadcastReceiver: BroadcastReceiver? = null

    private lateinit var wifiP2pManager: WifiP2pManager

    private lateinit var wifiP2pChannel: WifiP2pManager.Channel

    private var wifiP2pInfo: WifiP2pInfo? = null

    private var wifiP2pEnabled = false

    private fun initPauseResumeButton() {
        val btnPauseResume = findViewById<Button>(R.id.btnPauseResume)
        btnPauseResume.setOnClickListener {
            if (isTransferPaused) {
                // Wznowienie transferu
                resumeTransfer()
                btnPauseResume.text = "Pause"
            } else {
                pauseTransfer()
                btnPauseResume.text = "Resume"
            }
            isTransferPaused = !isTransferPaused // Zmieniamy stan
        }
    }

    private fun showTrustedDeviceDialog(deviceAddress: String) {
        AlertDialog.Builder(this)
            .setTitle("Add Trusted Device")
            .setMessage("Do you want to add this device to your trusted devices?")
            .setPositiveButton("Yes") { _, _ ->
                TrustedDeviceManager.addTrustedDevice(this, deviceAddress)
                showToast("Device added to trusted devices")
            }
            .setNegativeButton("No", null)
            .show()
    }

    private val directActionListener = object : DirectActionListener {
        private var isConnectingLogged = false
        private var isConnecting = false

        override fun wifiP2pEnabled(enabled: Boolean) {
            wifiP2pEnabled = enabled
            log("Wi-Fi P2P Enabled: $enabled")
        }

        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            isConnecting = false
            if (!isConnectingLogged) {
                dismissLoadingDialog()
                this@FileSenderActivity.wifiP2pInfo = wifiP2pInfo

                // Zaktualizowanie stanu przycisków
                btnConnect.text = "Connected"
                btnConnect.isEnabled = false
                btnSendFile.visibility = View.VISIBLE
                btnSendFile.isEnabled = true

                // Czyszczenie listy urządzeń po udanym połączeniu
                wifiP2pDeviceList.clear()
                deviceAdapter.notifyDataSetChanged()

                // Włączenie przycisków do rozłączania i wyboru pliku
                btnDisconnect.isEnabled = true
                btnChooseFile.isEnabled = true

                log("Connection established with group owner: ${wifiP2pInfo.groupOwnerAddress.hostAddress}")
                log("Group formed: ${wifiP2pInfo.groupFormed}, Is Group Owner: ${wifiP2pInfo.isGroupOwner}")

                // Wyświetlenie szczegółów połączenia
                val connectionDetails = StringBuilder().apply {
                    append("Group Owner Status: ")
                    append(if (wifiP2pInfo.isGroupOwner) "Group Owner" else "Not Group Owner")
                    append("\nGroup Owner IP Address: ${wifiP2pInfo.groupOwnerAddress.hostAddress}")
                }
                tvConnectionStatus.text = connectionDetails

                isConnectingLogged = true // Ustawienie flagi, aby uniknąć wielokrotnego logowania
            }
        }

        override fun onDisconnection() {
            isConnecting = false
            log("Disconnection event triggered")
            log("Disconnected from the group")

            // Resetowanie przycisków i widoczności
            btnConnect.text = "Connect"
            btnConnect.isEnabled = true
            btnSendFile.visibility = View.GONE
            btnSendFile.isEnabled = false
            btnDisconnect.isEnabled = false
            btnChooseFile.isEnabled = false

            // Czyszczenie listy urządzeń i stanu połączenia
            wifiP2pDeviceList.clear()
            deviceAdapter.notifyDataSetChanged()
            wifiP2pInfo = null
            tvConnectionStatus.text = null

            // Resetowanie flagi logowania
            isConnectingLogged = false

            // Komunikat dla użytkownika
            showToast("Disconnected from the group")
        }


        override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
            log("onSelfDeviceAvailable")
            log("DeviceName: " + wifiP2pDevice.deviceName)
            log("DeviceAddress: " + wifiP2pDevice.deviceAddress)
            log("Status: " + wifiP2pDevice.status)
            val log = "deviceName: " + wifiP2pDevice.deviceName + "\n" +
                    "deviceAddress: " + wifiP2pDevice.deviceAddress + "\n" +
                    "deviceStatus: " + WifiP2pUtils.getDeviceStatus(wifiP2pDevice.status)
            tvDeviceState.text = log
        }

        override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice>) {
            log("onPeersAvailable: ${wifiP2pDeviceList.size}")
            this@FileSenderActivity.wifiP2pDeviceList.clear()
            this@FileSenderActivity.wifiP2pDeviceList.addAll(wifiP2pDeviceList)
            deviceAdapter.notifyDataSetChanged()

            if (wifiP2pDeviceList.isNotEmpty() && !isConnecting) {
                val firstDevice = wifiP2pDeviceList.first()
                isConnecting = true // Zabezpieczenie przed wielokrotnym wywołaniem
                connect(firstDevice)
                log("Attempting to connect to: ${firstDevice.deviceName}")
            } else if (wifiP2pDeviceList.isEmpty()) {
                log("No peers available")
                showToast("No peers available")
            }
            dismissLoadingDialog()
        }

        override fun onChannelDisconnected() {
            log("Wi-Fi P2P channel disconnected")
            showToast("Wi-Fi Direct channel disconnected. Attempting to reconnect...")

            wifiP2pChannel = wifiP2pManager.initialize(this@FileSenderActivity, mainLooper, channelListener)

            if (wifiP2pChannel == null) {
                log("Failed to reinitialize Wi-Fi P2P channel")
                showToast("Failed to reinitialize Wi-Fi Direct. Please restart the app.")
            } else {
                log("Wi-Fi P2P channel reinitialized successfully")
            }
        }


    }

    private val channelListener = WifiP2pManager.ChannelListener {
        log("Wi-Fi P2P channel reinitialized")
        showToast("Wi-Fi Direct channel reinitialized successfully")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_sender)
        initPauseResumeButton()
        initView()
        // Sprawdzanie uprawnień
        if (!arePermissionsGranted()) {
            requestPermissions(requiredPermissions, PERMISSION_REQUEST_CODE)
        } else {
            initDevice()
        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            requiredPermissions += Manifest.permission.NEARBY_WIFI_DEVICES
//        } else {
//            requiredPermissions += Manifest.permission.ACCESS_COARSE_LOCATION
//            requiredPermissions += Manifest.permission.ACCESS_FINE_LOCATION
//        }
        initEvent()
        supportActionBar?.apply {
            title = "File Sender"
            setDisplayHomeAsUpEnabled(true)
        }
        tvTrustedDevices = findViewById(R.id.tvTrustedDevices)
        btnPauseResume.setOnClickListener {
            if (isTransferPaused) {
                resumeTransfer() // wznowienie transferu
                btnPauseResume.text = "Pause"
            } else {
                pauseTransfer() // zatrzymanie transferu
                btnPauseResume.text = "Resume"
            }
            isTransferPaused = !isTransferPaused
        }

        btnConnect.setOnClickListener {
            if (wifiP2pDeviceList.isNotEmpty()) {
                val firstDevice = wifiP2pDeviceList.first()
                connect(firstDevice)
                btnConnect.text = "Connecting..."
                btnConnect.isEnabled = false
            } else {
                showToast("No devices available to connect")
            }
        }

        btnSendFile.setOnClickListener {
            if (currentFileUri == null) {
                showToast("No file selected")
                return@setOnClickListener
            }

            val ipAddress = wifiP2pInfo?.groupOwnerAddress?.hostAddress
            if (ipAddress.isNullOrBlank()) {
                showToast("Connection error: No group owner address")
                return@setOnClickListener
            }

            log("Sending file to $ipAddress with URI: $currentFileUri")
            fileSenderViewModel.send(ipAddress = ipAddress, fileUri = currentFileUri!!)
            btnSendFile.isEnabled = false
            showToast("File is being sent...")
        }
        // Dodane sprawdzenie stanu Wi-Fi Direct
        checkWifiP2pState()
    }

    private val PERMISSION_REQUEST_CODE = 1001

    // Sprawdza, czy wszystkie uprawnienia zostały przyznane
    private fun arePermissionsGranted(): Boolean {
        return requiredPermissions.all { permission ->
            ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun pauseTransfer() {
        fileSenderViewModel.pauseTransfer()
        isTransferPaused = true
    }

    private fun checkWifiP2pState() {
        // Sprawdzenie uprawnień w zależności od wersji Androida
        val missingPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add("NEARBY_WIFI_DEVICES")
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add("ACCESS_FINE_LOCATION")
            }
        }

        if (missingPermissions.isNotEmpty()) {
            log("Missing required permissions: ${missingPermissions.joinToString()}")
            showToast("Please grant necessary permissions to use Wi-Fi Direct.")
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions,
                PERMISSION_REQUEST_CODE
            )
            return
        }

        // Jeśli uprawnienia są dostępne, sprawdzamy stan Wi-Fi P2P
        wifiP2pManager.requestGroupInfo(wifiP2pChannel) { group ->
            if (group != null) {
                log("Wi-Fi P2P group available: ${group.networkName}")
                showToast("Wi-Fi Direct is enabled and a group is active.")
            } else {
                log("No active Wi-Fi P2P group found")
                showToast("Wi-Fi Direct is enabled but no group is active.")
            }
        }

        wifiP2pManager.requestPeers(wifiP2pChannel) { peers ->
            if (peers.deviceList.isNotEmpty()) {
                log("Available peers: ${peers.deviceList.size}")
                showToast("Available peers found: ${peers.deviceList.size}")
            } else {
                log("No peers available")
                showToast("No peers available")
            }
        }

        if (!wifiP2pEnabled) {
            log("Wi-Fi P2P is disabled")
            showToast("Please enable Wi-Fi Direct in device settings.")
        } else {
            log("Wi-Fi P2P is enabled")
        }
    }


    // Stała dla kodu żądania uprawnień
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initDevice()
            } else {
                showToast("Required permissions not granted. Please enable them in settings.")
            }
        }
    }

    private fun resumeTransfer() {
        val ipAddress = wifiP2pInfo?.groupOwnerAddress?.hostAddress
        val fileUri = currentFileUri
        if (!ipAddress.isNullOrBlank() && fileUri != null) {
            fileSenderViewModel.resumeTransfer(ipAddress, fileUri)
            log("Transfer resumed")
        } else {
            log("Unable to resume transfer: missing file URI or IP address.")
        }
    }

    override fun onResume() {
        super.onResume()
        updateTrustedDevicesList()
    }

    private fun updateTrustedDevicesList() {
        val trustedDevicesText = TrustedDeviceManager.getTrustedDevicesAsString(this)
        tvTrustedDevices.text = trustedDevicesText
    }

    @SuppressLint("MissingPermission")
    private fun initView() {
        supportActionBar?.title = "File Sender"
        btnDisconnect.setOnClickListener {
            disconnect()
        }
        btnChooseFile.setOnClickListener {
            if (arePermissionsGranted()) {
                imagePickerLauncher.launch("image/*")
            } else {
                showToast("Missing permissions to access files. Please grant them in settings.")
            }
        }
        btnDirectDiscover.setOnClickListener {
            if (!wifiP2pEnabled) {
                showToast("Please enable Wi-Fi Direct in settings")
                return@setOnClickListener
            }
            showLoadingDialog()
            wifiP2pDeviceList.clear()
            deviceAdapter.notifyDataSetChanged()
            wifiP2pManager.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    showToast("Discover peers initiated successfully")
                    dismissLoadingDialog()
                    showToast("Discovering peers...")
                }

                override fun onFailure(reasonCode: Int) {
                    dismissLoadingDialog()
                    when (reasonCode) {
                        WifiP2pManager.BUSY -> log("Discover failed: Manager is busy")
                        WifiP2pManager.ERROR -> log("Discover failed: Internal error")
                        WifiP2pManager.P2P_UNSUPPORTED -> log("Discover failed: Wi-Fi Direct unsupported")
                        else -> log("Discover failed: Unknown error ($reasonCode)")
                    }
                }
            })
        }
        deviceAdapter.onItemClickListener = object : OnItemClickListener {
            override fun onItemClick(position: Int) {
                val wifiP2pDevice = wifiP2pDeviceList.getOrNull(position)
                if (wifiP2pDevice != null) {
                    if (TrustedDeviceManager.isTrustedDevice(this@FileSenderActivity, wifiP2pDevice.deviceAddress)) {
                        connect(wifiP2pDevice)
                    } else {
                        showTrustedDeviceDialog(wifiP2pDevice.deviceAddress)
                    }
                }
            }
        }
        rvDeviceList.adapter = deviceAdapter
        rvDeviceList.layoutManager = object : LinearLayoutManager(this) {
            override fun canScrollVertically(): Boolean {
                return false
            }
        }
    }

    private fun initDevice() {
        if (!arePermissionsGranted()) {
            log("Cannot initialize Wi-Fi P2P. Missing permissions.")
            return
        }

        // Inicjalizacja Wi-Fi P2P Manager
        val mWifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as? WifiP2pManager
        if (mWifiP2pManager == null) {
            finish()
            return
        }
        wifiP2pManager = mWifiP2pManager
        wifiP2pChannel = mWifiP2pManager.initialize(this, mainLooper, directActionListener)

        // Rejestracja BroadcastReceiver
        broadcastReceiver = DirectBroadcastReceiver(
            wifiP2pManager = wifiP2pManager,
            wifiP2pChannel = wifiP2pChannel,
            directActionListener = directActionListener
        )
        registerReceiver(broadcastReceiver, DirectBroadcastReceiver.getIntentFilter())

        log("Wi-Fi P2P initialized and receiver registered.")
    }



    private fun initEvent() {
        // Uruchamianie kolektora stanu przesyłu plików
        lifecycleScope.launch {
            fileSenderViewModel.fileTransferViewState.collect { state ->
                when (state) {
                    is FileTransferViewState.Idle -> {
                        clearLog()
                        dismissLoadingDialog()
                        btnPauseResume.visibility = View.GONE
                        progressBar.visibility = View.GONE
                    }
                    is FileTransferViewState.Connecting -> {
                        showLoadingDialog()
                        btnPauseResume.visibility = View.GONE
                        progressBar.visibility = View.GONE
                    }
                    is FileTransferViewState.Receiving -> {
                        showLoadingDialog()
                        btnPauseResume.visibility = View.VISIBLE
                        progressBar.visibility = View.VISIBLE
                    }
                    is FileTransferViewState.Progress -> {
                        progressBar.visibility = View.VISIBLE
                        btnPauseResume.visibility = View.VISIBLE
                        progressBar.progress = state.progress // ustawienie postępu na podstawie wartości state.progress
                    }
                    is FileTransferViewState.Success -> {
                        dismissLoadingDialog()
                        btnPauseResume.visibility = View.GONE
                        progressBar.visibility = View.GONE
                    }
                    is FileTransferViewState.Failed -> {
                        dismissLoadingDialog()
                        btnPauseResume.visibility = View.GONE
                        progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver)
        }
    }

    private var isAlreadyConnecting = false

    @SuppressLint("MissingPermission")
    private fun connect(wifiP2pDevice: WifiP2pDevice) {
        if (isAlreadyConnecting) {
            log("Already trying to connect to a device")
            return
        }
        isAlreadyConnecting = true

        val wifiP2pConfig = WifiP2pConfig().apply {
            deviceAddress = wifiP2pDevice.deviceAddress
            wps.setup = WpsInfo.PBC
        }

        wifiP2pManager.connect(wifiP2pChannel, wifiP2pConfig, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                log("Successfully initiated connection to: ${wifiP2pDevice.deviceName}")
                showToast("Connecting to ${wifiP2pDevice.deviceName}")
            }

            override fun onFailure(reason: Int) {
                isAlreadyConnecting = false // Reset flagi w przypadku niepowodzenia
                when (reason) {
                    WifiP2pManager.BUSY -> log("Connection failed: Wi-Fi P2P Manager is busy")
                    WifiP2pManager.ERROR -> log("Connection failed: Internal error")
                    WifiP2pManager.P2P_UNSUPPORTED -> log("Connection failed: Wi-Fi Direct is unsupported on this device")
                    else -> log("Connection failed: Unknown error ($reason)")
                }
                showToast("Failed to connect to ${wifiP2pDevice.deviceName}")
            }
        })
    }

    private fun disconnect() {
        wifiP2pManager.cancelConnect(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onFailure(reasonCode: Int) {
                log("cancelConnect onFailure: $reasonCode")
            }

            override fun onSuccess() {
                log("cancelConnect onSuccess")
                tvConnectionStatus.text = null
                btnDisconnect.isEnabled = false
                btnChooseFile.isEnabled = false
            }
        })
        wifiP2pManager.removeGroup(wifiP2pChannel, null)
    }

    private fun log(log: String) {
        tvLog.append(log)
        tvLog.append("\n\n")
    }

    private fun clearLog() {
        tvLog.text = ""
    }
}
