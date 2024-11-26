package pl.dawidolko.wifidirect.FileActivity

import android.content.Intent
import android.net.Uri
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
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.SocketException
import java.text.SimpleDateFormat
import java.util.*

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Activity odpowiedzialne za odbieranie plików przy użyciu Wi-Fi Direct.
 * Obsługuje połączenie serwera, zapis pliku w folderze "Download" oraz wyświetlanie stanu odbierania.
 */

class FileReceiverActivity : AppCompatActivity() {

    private var port = 8778
    private var serverSocket: ServerSocket? = null
    private var lastReceivedFile: File? = null

    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_receiver)

        // Inicjalizacja przycisków i widoków
        val btnStartReceive = findViewById<Button>(R.id.btnStartReceive)
        val btnStopReceive = findViewById<Button>(R.id.btnStopReceive)
        val btnOpenDownloads = findViewById<Button>(R.id.btnOpenDownloads)

        supportActionBar?.apply {
            title = "File Receiver"
            setDisplayHomeAsUpEnabled(true)
        }

        // Funkcja rozpoczynająca odbieranie pliku
        btnStartReceive.setOnClickListener {
            if (isListening) {
                Toast.makeText(this, "Already listening for connections.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startReceiving()
            Log.d("FileReceiver", "Start Receiving clicked")
            Thread {
                try {
                    if (serverSocket?.isBound == true) {
                        Log.d("FileReceiver", "Closing existing socket on port $port.")
                        serverSocket?.close()
                    }
                    serverSocket = ServerSocket(port)
                    Log.d("FileReceiver", "Waiting for connection on port $port")
                    val clientSocket = serverSocket!!.accept()
                    val inputStream = clientSocket.getInputStream()

                    // Ścieżka zapisu pliku
                    val downloadsDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val formattedName =
                        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val newFileName = "${formattedName}_received_file.jpg"
                    val file = File(downloadsDir, newFileName)

                    Log.d("FileReceiver", "Saving received file to: ${file.absolutePath}")
                    val outputStream = FileOutputStream(file)
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                    inputStream.close()
                    clientSocket.close()

                    lastReceivedFile = file

                    // Dodanie do historii
                    val timestamp =
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val historyItem = HistoryItem(newFileName, timestamp, false) // false - odebrany
                    saveHistoryItem(historyItem)

                    // Powiadomienie o sukcesie i otwarcie folderu
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "File received: ${file.name}.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: SocketException) {
                    Log.e("FileReceiver", "SocketException: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this, "SocketException: ${e.message}", Toast.LENGTH_SHORT)
                            .show()
                    }
                } catch (e: Exception) {
                    Log.e("FileReceiver", "Error while receiving file: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        // Funkcja zatrzymująca odbieranie pliku
        btnStopReceive.setOnClickListener {
            try {
                Log.d("FileReceiver", "Stopping receiving, closing server socket.")
                serverSocket?.close()
                Toast.makeText(
                    this,
                    "Port closed and ready for new connection.",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e("FileReceiver", "Error closing server socket: ${e.message}")
                Toast.makeText(this, "Error closing port: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnStopReceive.setOnClickListener {
            stopReceiving()
        }

        // Funkcja otwierająca folder pobranych plików
        btnOpenDownloads.setOnClickListener {
            openDownloadsFolder()
        }
    }

    private fun startReceiving() {
        Thread {
            try {
                if (serverSocket?.isBound == true) {
                    Log.d("FileReceiver", "Closing existing socket on port $port.")
                    serverSocket?.close()
                }
                serverSocket = ServerSocket(port)
                isListening = true
                runOnUiThread {
                    Toast.makeText(this, "Listening for connections on port $port.", Toast.LENGTH_SHORT).show()
                }

                val clientSocket = serverSocket!!.accept()
                val inputStream = clientSocket.getInputStream()

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val formattedName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(downloadsDir, "${formattedName}_received_file.jpg")

                val outputStream = FileOutputStream(file)
                inputStream.copyTo(outputStream)
                outputStream.close()
                inputStream.close()
                clientSocket.close()

                isListening = false
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val historyItem = HistoryItem(file.name, timestamp, false)
                saveHistoryItem(historyItem)

                runOnUiThread {
                    Toast.makeText(this, "File received: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SocketException) {
                Log.e("FileReceiver", "SocketException: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "Connection error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FileReceiver", "Error while receiving file: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun stopReceiving() {
        try {
            serverSocket?.close()
            isListening = false
            Toast.makeText(this, "Stopped listening for connections.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping connection: ${e.message}", Toast.LENGTH_SHORT).show()
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
