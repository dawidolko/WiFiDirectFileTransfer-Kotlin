package github.leavesczy.wifip2p.sender

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.leavesczy.wifip2p.common.Constants
import github.leavesczy.wifip2p.common.FileTransfer
import github.leavesczy.wifip2p.common.FileTransferViewState
import github.leavesczy.wifip2p.common.TransferHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

class FileSenderViewModel(context: Application) : AndroidViewModel(context) {

    private val _fileTransferViewState = MutableSharedFlow<FileTransferViewState>()
    val fileTransferViewState: SharedFlow<FileTransferViewState> get() = _fileTransferViewState

    private val _log = MutableSharedFlow<String>()
    val log: SharedFlow<String> get() = _log

    private var job: Job? = null
    private var isPaused = false
    private var bytesTransferred = 0L
    private var totalBytes = 0L

    private val _progress = MutableSharedFlow<Int>()
    val progress: SharedFlow<Int> get() = _progress

    fun send(ipAddress: String, fileUri: Uri) {
        if (job != null) return

        job = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _fileTransferViewState.emit(FileTransferViewState.Idle)

                var socket: Socket? = null
                var outputStream: OutputStream? = null
                var objectOutputStream: ObjectOutputStream? = null
                var fileInputStream: FileInputStream? = null
                try {
                    // Przygotowanie pliku do przesyłania
                    val cacheFile = saveFileToCacheDir(context = getApplication(), fileUri = fileUri)
                    val fileTransfer = FileTransfer(fileName = cacheFile.name, fileSize = cacheFile.length())
                    _log.emit("Preparing to send file: ${fileTransfer.fileName}, size: ${cacheFile.length()} bytes")
                    _log.emit("Opening socket connection to $ipAddress")

                    // Konfiguracja i otwarcie gniazda
                    socket = Socket()
                    socket.bind(null)
                    socket.connect(InetSocketAddress(ipAddress, Constants.PORT), 30000)
                    _log.emit("Socket connected to $ipAddress on port ${Constants.PORT}")

                    // Konfiguracja strumieni
                    outputStream = socket.getOutputStream()
                    objectOutputStream = ObjectOutputStream(outputStream)
                    objectOutputStream.writeObject(fileTransfer)
                    _log.emit("File metadata sent: ${fileTransfer.fileName}")

                    fileInputStream = FileInputStream(cacheFile)

                    // Przesyłanie danych
                    val buffer = ByteArray(1024 * 100) // 100 KB
                    var length: Int
                    while (fileInputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                        _log.emit("Sent chunk of size: $length bytes")
                    }

                    _fileTransferViewState.emit(FileTransferViewState.Success(file = cacheFile))
                    _log.emit("File transfer completed. Total bytes sent: ${cacheFile.length()}")

                    // Dodanie do historii
                    TransferHistoryManager.addTransferRecord(
                        getApplication(),
                        "Sent: ${fileTransfer.fileName}"
                    )

                    // Powiadomienie użytkownika o sukcesie
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            getApplication(),
                            "File sent successfully: ${fileTransfer.fileName}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                } catch (e: Throwable) {
                    val errorMessage = when (e) {
                        is java.net.SocketTimeoutException -> "Connection timed out"
                        is java.net.ConnectException -> "Failed to connect to $ipAddress"
                        is java.io.IOException -> "I/O error occurred during file transfer"
                        else -> "Unexpected error: ${e.message}"
                    }
                    _log.emit("Error during file transfer: $errorMessage")
                    _fileTransferViewState.emit(FileTransferViewState.Failed(throwable = e))

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            getApplication(),
                            "Error: $errorMessage",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    try {
                        fileInputStream?.close()
                        outputStream?.close()
                        objectOutputStream?.close()
                        socket?.close()
                        _log.emit("Resources closed successfully")
                    } catch (e: Throwable) {
                        _log.emit("Error closing resources: ${e.message}")
                    }
                }
            }
        }

        job?.invokeOnCompletion { job = null }
    }

    fun pauseTransfer() {
        isPaused = true
    }

    fun resumeTransfer(ipAddress: String, fileUri: Uri) {
        isPaused = false
        send(ipAddress, fileUri)
    }

    private fun saveTransferProgress(progress: Long) {
        val prefs = getApplication<Application>().getSharedPreferences("transferPrefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("bytesTransferred", progress).apply()
    }

    private fun getSavedTransferProgress(): Long {
        val prefs = getApplication<Application>().getSharedPreferences("transferPrefs", Context.MODE_PRIVATE)
        return prefs.getLong("bytesTransferred", 0)
    }

    private fun clearTransferProgress() {
        val prefs = getApplication<Application>().getSharedPreferences("transferPrefs", Context.MODE_PRIVATE)
        prefs.edit().remove("bytesTransferred").apply()
    }

    private suspend fun saveFileToCacheDir(context: Context, fileUri: Uri): File {
        return withContext(Dispatchers.IO) {
            val documentFile = DocumentFile.fromSingleUri(context, fileUri)
                ?: throw NullPointerException("fileName for given input Uri is null")
            val fileName = documentFile.name
            val outputFile =
                File(context.cacheDir, Random.nextInt(1, 200).toString() + "_" + fileName)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.createNewFile()
            val outputFileUri = Uri.fromFile(outputFile)
            copyFile(context, fileUri, outputFileUri)
            return@withContext outputFile
        }
    }

    private suspend fun copyFile(context: Context, inputUri: Uri, outputUri: Uri) {
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw NullPointerException("InputStream for given input Uri is null")
            val outputStream = FileOutputStream(outputUri.toFile())
            val buffer = ByteArray(1024)
            var length: Int
            while (true) {
                length = inputStream.read(buffer)
                if (length > 0) {
                    outputStream.write(buffer, 0, length)
                } else {
                    break
                }
            }
            inputStream.close()
            outputStream.close()
        }
    }
}
