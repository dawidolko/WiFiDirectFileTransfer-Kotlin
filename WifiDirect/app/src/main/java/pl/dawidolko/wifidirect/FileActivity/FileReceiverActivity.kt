package pl.dawidolko.wifidirect.FileActivity

import android.content.Intent
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import pl.dawidolko.wifidirect.HistoryActivity.HistoryItem
import pl.dawidolko.wifidirect.R
import java.io.*
import java.net.ServerSocket
import java.net.SocketException
import java.text.SimpleDateFormat
import java.util.*

class FileReceiverActivity : AppCompatActivity() {

    private var port = 8778
    private var serverSocket: ServerSocket? = null

    private var isReceiving = false
    private var receiveThread: Thread? = null

    private val controlPort = 8888
    private var controlServerSocket: ServerSocket? = null

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_receiver)

        val btnStartReceive = findViewById<Button>(R.id.btnStartReceive)
        val btnStopReceive = findViewById<Button>(R.id.btnStopReceive)
        val btnOpenDownloads = findViewById<Button>(R.id.btnOpenDownloads)

        supportActionBar?.apply {
            title = "File Receiver"
            setDisplayHomeAsUpEnabled(true)
        }

        manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager?.initialize(this, mainLooper, null)

        startControlServer()

        manager?.requestConnectionInfo(channel) { info ->
            if (info.groupFormed && info.isGroupOwner) {
                Log.d("FileReceiver", "Urządzenie jest Właścicielem Grupy. Uruchamiam serwer kontrolny.")
                startControlServer()
            } else {
                Log.d("FileReceiver", "Urządzenie nie jest Właścicielem Grupy. Nie uruchamiam serwera kontrolnego.")
            }
        }

        btnStartReceive.setOnClickListener {
            if (isReceiving) {
                Toast.makeText(this, "Already listening for connections.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startReceiving()
            Log.d("FileReceiver", "Start Receiving clicked")
        }

        btnStopReceive.setOnClickListener {
            stopReceiving()
        }

        btnOpenDownloads.setOnClickListener {
            openDownloadsFolder()
        }
    }

    private fun startReceiving() {
        isReceiving = true
        receiveThread = Thread {
            try {
                if (serverSocket?.isBound == true) {
                    Log.d("FileReceiver", "Closing existing socket on port $port.")
                    serverSocket?.close()
                }
                serverSocket = ServerSocket(port)
                Log.d("FileReceiver", "Waiting for connection on port $port")
                val clientSocket = serverSocket!!.accept()

                val inputStream = clientSocket.getInputStream()
                val outputStream = clientSocket.getOutputStream()

                val metadataReader = BufferedReader(InputStreamReader(inputStream))
                val metadataWriter = BufferedWriter(OutputStreamWriter(outputStream))

                val metadataLine = metadataReader.readLine()
                val fileName = metadataLine.substringAfter("FILENAME:").substringBefore("|FILESIZE:")
                val fileSizeStr = metadataLine.substringAfter("FILESIZE:")
                val fileSize = fileSizeStr.toLongOrNull() ?: 0L

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)

                var alreadyReceivedBytes = 0L
                if (file.exists()) {
                    alreadyReceivedBytes = file.length()
                }

                metadataWriter.write("OFFSET:$alreadyReceivedBytes\n")
                metadataWriter.flush()

                Log.d("FileReceiver", "Resuming from offset $alreadyReceivedBytes for file $fileName")

                val fileOutputStream = FileOutputStream(file, true)

                val buffer = ByteArray(4096)
                var totalReceived = alreadyReceivedBytes
                while (true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    fileOutputStream.write(buffer, 0, bytesRead)
                    totalReceived += bytesRead
                }

                fileOutputStream.close()
                inputStream.close()
                clientSocket.close()

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val historyItem = HistoryItem(fileName, timestamp, false)
                saveHistoryItem(historyItem)

                runOnUiThread {
                    Toast.makeText(this, "File received: ${file.name}.", Toast.LENGTH_SHORT).show()
                }

            } catch (e: SocketException) {
                Log.e("FileReceiver", "SocketException: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "SocketException: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FileReceiver", "Error while receiving file: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isReceiving = false
                serverSocket?.close()
            }
        }
        receiveThread?.start()
    }

    private fun stopReceiving() {
        if (isReceiving) {
            try {
                serverSocket?.close()
                isReceiving = false
                receiveThread?.interrupt()
                Toast.makeText(this, "Stopped listening for connections.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error stopping connection: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Not currently listening.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDownloadsFolder() {
        try {
            val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
            if (downloadsDir.exists()) {
                val uri = Uri.parse(downloadsDir.path)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                startActivity(Intent.createChooser(intent, "Open Downloads Folder"))
            } else {
                Toast.makeText(this, "Downloads folder does not exist.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open Downloads folder: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("FileReceiver", "Error opening Downloads folder: ${e.message}")
        }
    }

    private var isControlServerRunning = false

    private fun startControlServer() {
        if (isControlServerRunning) {
            Log.d("ControlServer", "Serwer kontrolny już działa.")
            return
        }

        isControlServerRunning = true
        Thread {
            try {
                controlServerSocket = ServerSocket(controlPort)
                Log.d("ControlServer", "Serwer kontrolny uruchomiony na porcie $controlPort")
                while (!Thread.currentThread().isInterrupted) {
                    val clientSocket = controlServerSocket!!.accept()
                    val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                    val clientIp = reader.readLine()
                    Log.d("ControlServer", "Odebrano adres IP klienta: $clientIp")
                    val sharedPreferences = getSharedPreferences("client_info", MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.putString("client_ip", clientIp)
                    editor.apply()
                    clientSocket.close()
                }
            } catch (e: Exception) {
                Log.e("ControlServer", "Error: ${e.message}")
            }
        }.start()
    }

    private fun saveHistoryItem(historyItem: HistoryItem) {
        val sharedPreferences = getSharedPreferences("file_history", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val historyListJson = sharedPreferences.getString("history_list", "[]")
        val gson = Gson()
        val historyList =
            gson.fromJson(historyListJson, Array<HistoryItem>::class.java).toMutableList()
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

    fun setPort(newPort: Int) {
        this.port = newPort
    }
}
