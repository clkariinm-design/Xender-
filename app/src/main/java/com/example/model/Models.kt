package com.example.model

enum class FileCategory(val label: String) {
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    DOCUMENTS("Docs"),
    APPS("Apps")
}

data class FileItem(
    val id: String,
    val name: String,
    val size: Long,
    val uri: android.net.Uri,
    val mimeType: String,
    val category: FileCategory,
    val dateModified: Long = 0L,
    val extraInfo: String = ""
)

enum class TransferStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

data class TransferRecord(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val isOutgoing: Boolean,
    val status: TransferStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String = ""
)

sealed interface TransferSessionState {
    data object Idle : TransferSessionState
    data class Listening(val ipAddress: String, val port: Int) : TransferSessionState
    data class Connecting(val peerIp: String, val port: Int) : TransferSessionState
    data class Transferring(
        val isOutgoing: Boolean,
        val peerIp: String,
        val currentFileName: String,
        val currentFileIndex: Int,
        val totalFiles: Int,
        val currentFileTransferred: Long,
        val currentFileSize: Long,
        val totalBytesTransferred: Long,
        val totalBytesSize: Long,
        val speedBytesPerSec: Long,
        val completedFiles: List<String> = emptyList()
    ) : TransferSessionState
    data class Completed(
        val totalFiles: Int,
        val totalBytes: Long,
        val isOutgoing: Boolean,
        val fileNames: List<String>
    ) : TransferSessionState
    data class Error(val message: String) : TransferSessionState
}
