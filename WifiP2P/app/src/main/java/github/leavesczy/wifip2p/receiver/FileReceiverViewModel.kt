package github.leavesczy.wifip2p.receiver

import android.app.AlertDialog
import android.app.Application
import android.widget.Toast
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
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket

class FileReceiverViewModel(context: Application) : AndroidViewModel(context) {

    private var isPaused = false
    private var serverSocket: ServerSocket? = null
    private var bytesTransferred = 0L
    private var totalBytes = 0L


    fun pauseReceiving() {
        isPaused = true
    }

    fun resumeReceiving() {
        isPaused = false
        startListener()
    }

    private val _fileTransferViewState = MutableSharedFlow<FileTransferViewState>()
    val fileTransferViewState: SharedFlow<FileTransferViewState> get() = _fileTransferViewState

    private val _log = MutableSharedFlow<String>()
    val log: SharedFlow<String> get() = _log

    private var job: Job? = null

    fun startListener() {
        if (job != null) return

        job = viewModelScope.launch(Dispatchers.IO) {
            _fileTransferViewState.emit(FileTransferViewState.Idle)

            try {
                closeServerSocket()
                _log.emit("Starting server socket on port ${Constants.PORT}")

                serverSocket = ServerSocket()
                serverSocket?.reuseAddress = true
                serverSocket?.bind(InetSocketAddress(Constants.PORT))
                _log.emit("Server socket is running. Waiting for connection...")

                val client = serverSocket?.accept()
                if (client != null) {
                    _log.emit("Client connected from: ${client.inetAddress.hostAddress}")
                    handleClient(client.getInputStream())
                }
            } catch (e: Exception) {
                _log.emit("Error in server socket: ${e.message}")
                _fileTransferViewState.emit(FileTransferViewState.Failed(e))
            } finally {
                closeServerSocket()
                job = null
            }
        }
    }

    private suspend fun handleClient(inputStream: InputStream) {
        log("Client connected, starting to receive file...")
        try {
            val objectInputStream = ObjectInputStream(inputStream)
            val fileTransfer = objectInputStream.readObject() as FileTransfer
            log("Receiving file: ${fileTransfer.fileName}")

            // Używamy emitowania stanu do informowania Activity
            withContext(Dispatchers.Main) {
                _fileTransferViewState.emit(FileTransferViewState.Receiving)
            }

            saveReceivedFile(inputStream, fileTransfer.fileName)

        } catch (e: Exception) {
            log("Error during file reception: ${e.message}")
            _fileTransferViewState.emit(FileTransferViewState.Failed(e))
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    getApplication(),
                    "Failed to receive file: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun saveReceivedFile(inputStream: InputStream, fileName: String) {
        val cacheDir = getApplication<Application>().getExternalFilesDir(null)
        val file = File(cacheDir, fileName)
        log("Saving file to: ${file.absolutePath}")

        FileOutputStream(file).use { fileOutputStream ->
            val buffer = ByteArray(1024)
            var bytesRead: Int
            var totalBytesTransferred = 0L
            while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                fileOutputStream.write(buffer, 0, bytesRead)
                totalBytesTransferred += bytesRead
                log("Transferred $bytesRead bytes. Total: $totalBytesTransferred bytes")
            }
        }

        log("File received successfully: ${file.absolutePath}")
        _fileTransferViewState.emit(FileTransferViewState.Success(file))
        TransferHistoryManager.addTransferRecord(
            getApplication(),
            "Received: $fileName"
        )
        withContext(Dispatchers.Main) {
            Toast.makeText(
                getApplication(),
                "File received successfully: $fileName",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private suspend fun log(message: String) {
        _log.emit(message)
    }

    private suspend fun closeServerSocket() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            log("Error closing server socket: ${e.message}")
        }
    }
}
