package github.leavesczy.wifip2p.common

import java.io.File
import java.io.Serializable

data class FileTransfer(
    val fileName: String,
    val fileSize: Long
) : Serializable

sealed class FileTransferViewState {
    object Idle : FileTransferViewState()
    object Connecting : FileTransferViewState()
    data class Progress(val progress: Int) : FileTransferViewState()
    data class Success(val file: File) : FileTransferViewState()
    data class Failed(val throwable: Throwable) : FileTransferViewState()
    object Receiving : FileTransferViewState()
}
