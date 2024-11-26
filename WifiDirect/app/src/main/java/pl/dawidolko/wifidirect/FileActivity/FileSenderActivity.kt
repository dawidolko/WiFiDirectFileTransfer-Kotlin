package pl.dawidolko.wifidirect.FileActivity

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import pl.dawidolko.wifidirect.R
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.gson.Gson
import pl.dawidolko.wifidirect.HistoryActivity.HistoryItem

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Activity odpowiedzialne za wysyłanie plików do innych urządzeń przy użyciu Wi-Fi Direct.
 * Zarządza wyborem pliku, progresją wysyłania oraz zapisaniem danych o transferze w historii.
 */

class FileSenderActivity : AppCompatActivity(), IpAddressCallback {

    private var fileUri: Uri? = null
    private var port = 8778
    private val SOCKET_TIMEOUT = 5000

    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_sender)

        supportActionBar?.apply {
            title = "File Sender"
            setDisplayHomeAsUpEnabled(true)
        }

        val btnChooseFile = findViewById<Button>(R.id.btnChooseFile)
        val btnSendFile = findViewById<Button>(R.id.btnSendFile)
        val tvSelectedFile = findViewById<TextView>(R.id.tvSelectedFile)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        btnChooseFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
            }
            filePickerLauncher.launch(intent)
        }

        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                fileUri = result.data?.data
                val fileSize = contentResolver.openFileDescriptor(fileUri!!, "r")?.statSize ?: 0
                tvSelectedFile.text =
                    "Selected file: ${fileUri?.lastPathSegment}, Size: ${fileSize / 1024} KB"
                Toast.makeText(this, "File selected: ${fileUri?.lastPathSegment}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSendFile.setOnClickListener {
            if (fileUri == null) {
                Toast.makeText(this, "Please select a file first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Thread {
                try {
                    getReceiverIpAddress(this, 9999)
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    private fun getReceiverIpAddress(callback: IpAddressCallback, receiverPort: Int) {
        val manager: WifiP2pManager? = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        val channel: WifiP2pManager.Channel? = manager?.initialize(this, mainLooper, null)

        manager?.requestConnectionInfo(channel, object : WifiP2pManager.ConnectionInfoListener {
            override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
                info?.groupOwnerAddress?.hostAddress?.let {
                    callback.onIpAddressReceived(it)
                }
            }
        })
    }

    override fun onIpAddressReceived(ipAddress: String) {
        Thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, port), SOCKET_TIMEOUT)

                val outputStream: OutputStream = socket.getOutputStream()
                val inputStream = contentResolver.openInputStream(fileUri!!)

                val fileSize = inputStream?.available() ?: 0
                var bytesTransferred = 0
                val buffer = ByteArray(4096)

                runOnUiThread {
                    progressBar.visibility = ProgressBar.VISIBLE
                    progressText.visibility = TextView.VISIBLE
                    progressBar.progress = 0
                    progressText.text = "0%"
                }

                var bytesRead: Int
                while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    bytesTransferred += bytesRead

                    val progress = (bytesTransferred * 100) / fileSize
                    runOnUiThread {
                        progressBar.progress = progress
                        progressText.text = "$progress%"
                    }
                }

                inputStream?.close()
                outputStream.close()
                socket.close()

                val fileName_ = fileUri?.lastPathSegment ?: "file"
                val formattedName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val newFileName = "$fileName_$formattedName"

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val historyItem = HistoryItem(newFileName, timestamp, true) // true - sent
                saveHistoryItem(historyItem)

                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    progressText.visibility = TextView.GONE
                    Toast.makeText(this, "File '$newFileName' sent successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    progressText.visibility = TextView.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
