package pl.dawidolko.wifidirect.FileActivity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import pl.dawidolko.wifidirect.HistoryActivity.HistoryItem
import pl.dawidolko.wifidirect.R
import java.io.*
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import android.net.wifi.p2p.WifiP2pManager

class FileSenderActivity : AppCompatActivity(), IpAddressCallback {

    private var fileUri: Uri? = null
    private var fileName_: String = "file"
    private var port = 8778
    private val SOCKET_TIMEOUT = 5000

    private var isPaused = false
    private lateinit var btnPauseResume: Button

    private val controlPort = 8888

    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_sender)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }

        supportActionBar?.apply {
            title = "File Sender"
            setDisplayHomeAsUpEnabled(true)
        }

        btnPauseResume = findViewById(R.id.btnPauseResume)
        val btnChooseFile = findViewById<Button>(R.id.btnChooseFile)
        val btnSendFile = findViewById<Button>(R.id.btnSendFile)
        val tvSelectedFile = findViewById<TextView>(R.id.tvSelectedFile)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager?.initialize(this, mainLooper, null)

        manager?.requestConnectionInfo(channel) { info ->
            val groupOwnerAddress = info.groupOwnerAddress?.hostAddress
            val isGroupOwner = info.isGroupOwner

            if (info.groupFormed && !isGroupOwner && groupOwnerAddress != null) {
                Log.d("FileSender", "Urządzenie jest klientem. Wysyłam adres IP do Właściciela Grupy.")
                sendIpAddressToGroupOwner(groupOwnerAddress)
            }
        }

        btnChooseFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
            }
            filePickerLauncher.launch(intent)
        }

        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                fileUri = result.data?.data
                if (fileUri != null) {
                    val fileSize = contentResolver.openFileDescriptor(fileUri!!, "r")?.statSize ?: 0
                    fileName_ = getFileNameFromUri(fileUri!!) ?: "file"
                    val sizeKB = fileSize / 1024
                    findViewById<TextView>(R.id.tvSelectedFile).text =
                        "Selected file: $fileName_, Size: ${sizeKB} KB"
                    Toast.makeText(this, "File selected: $fileName_", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSendFile.setOnClickListener {
            if (fileUri == null) {
                Toast.makeText(this, "Please select a file first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            getReceiverIpAddress(this)
        }

        btnPauseResume.setOnClickListener {
            if (!isPaused) {
                isPaused = true
                btnPauseResume.text = "Resume"
            } else {
                isPaused = false
                btnPauseResume.text = "Pause"
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        // Jeśli nie uda się pobrać z OpenableColumns, spróbuj lastPathSegment
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name
    }

    private fun getReceiverIpAddress(callback: IpAddressCallback) {
        manager?.requestConnectionInfo(channel) { info ->
            val groupOwnerAddress = info.groupOwnerAddress?.hostAddress
            val isGroupOwner = info.isGroupOwner

            val localIpAddress = getLocalIpAddress()
            val targetIpAddress: String?

            Log.d("FileSender", "GroupOwnerAddress: $groupOwnerAddress")
            Log.d("FileSender", "IsGroupOwner: $isGroupOwner")
            Log.d("FileSender", "LocalIpAddress: $localIpAddress")

            if (groupOwnerAddress != null && localIpAddress != null) {
                if (isGroupOwner) {
                    val sharedPreferences = getSharedPreferences("client_info", MODE_PRIVATE)
                    targetIpAddress = sharedPreferences.getString("client_ip", null)
                    Log.d("FileSender", "Urządzenie jest Właścicielem Grupy. Adres IP klienta: $targetIpAddress")

                    if (targetIpAddress == null) {
                        runOnUiThread {
                            Toast.makeText(this, "Adres IP klienta nie jest dostępny.", Toast.LENGTH_SHORT).show()
                        }
                        return@requestConnectionInfo
                    }
                } else {
                    targetIpAddress = groupOwnerAddress
                    Log.d("FileSender", "Urządzenie jest klientem. Adres IP Właściciela Grupy: $targetIpAddress")
                }

                callback.onIpAddressReceived(targetIpAddress)
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Informacje o połączeniu są niedostępne.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun onIpAddressReceived(ipAddress: String) {
        Thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, port), SOCKET_TIMEOUT)
                val outputStream: OutputStream = socket.getOutputStream()
                val inputStream = contentResolver.openInputStream(fileUri!!)

                val fileSize = inputStream?.available() ?: 0

                // Wysyłanie metadanych do odbiorcy:
                val metadataWriter = BufferedWriter(OutputStreamWriter(outputStream))
                val metadataReader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val metadataMessage = "FILENAME:$fileName_|FILESIZE:$fileSize\n"
                metadataWriter.write(metadataMessage)
                metadataWriter.flush()

                val offsetResponse = metadataReader.readLine()
                val offset = if (offsetResponse.startsWith("OFFSET:")) {
                    offsetResponse.substringAfter("OFFSET:").toLongOrNull() ?: 0L
                } else 0L

                if (offset > 0) {
                    inputStream?.skip(offset)
                }

                var bytesTransferred = offset.toInt()
                val buffer = ByteArray(4096)

                runOnUiThread {
                    progressBar.visibility = ProgressBar.VISIBLE
                    progressText.visibility = TextView.VISIBLE
                    btnPauseResume.visibility = Button.VISIBLE
                    val progress = if (fileSize > 0) (bytesTransferred * 100 / fileSize) else 0
                    progressBar.progress = progress
                    progressText.text = "$progress%"
                }

                var bytesRead: Int
                loop@ while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {

                    while (isPaused) {
                        Thread.sleep(100)
                    }

                    if (bytesRead == -1) break@loop

                    outputStream.write(buffer, 0, bytesRead)
                    bytesTransferred += bytesRead

                    val progress = if (fileSize > 0) (bytesTransferred * 100) / fileSize else 0
                    runOnUiThread {
                        progressBar.progress = progress
                        progressText.text = "$progress%"
                    }
                }

                inputStream?.close()
                outputStream.close()
                socket.close()

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val historyItem = HistoryItem(fileName_, timestamp, true)
                saveHistoryItem(historyItem)

                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    progressText.visibility = TextView.GONE
                    btnPauseResume.visibility = Button.GONE
                    Toast.makeText(this, "File '$fileName_' sent successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    progressText.visibility = TextView.GONE
                    btnPauseResume.visibility = Button.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("FileSender", "Error while sending file: ${e.message}")
                }
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Location permission is required for Wi-Fi Direct", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendIpAddressToGroupOwner(groupOwnerAddress: String) {
        Thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(groupOwnerAddress, controlPort), SOCKET_TIMEOUT)
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                val localIp = getLocalIpAddress()
                writer.write(localIp)
                writer.newLine()
                writer.flush()
                socket.close()
                Log.d("FileSender", "Wysłano adres IP do Właściciela Grupy: $localIp")
            } catch (e: Exception) {
                Log.e("FileSender", "Błąd podczas wysyłania adresu IP: ${e.message}")
            }
        }.start()
    }

    private fun saveHistoryItem(historyItem: HistoryItem) {
        val sharedPreferences = getSharedPreferences("file_history", MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val historyListJson = sharedPreferences.getString("history_list", "[]")
        val gson = Gson()
        val historyList = gson.fromJson(historyListJson, Array<HistoryItem>::class.java).toMutableList()

        historyList.add(historyItem)

        val updatedHistoryJson = gson.toJson(historyList)
        editor.putString("history_list", updatedHistoryJson)
        editor.apply()
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
}
