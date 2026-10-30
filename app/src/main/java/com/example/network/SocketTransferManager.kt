package com.example.network

import android.content.Context
import android.os.Environment
import com.example.model.FileItem
import com.example.model.TransferRecord
import com.example.model.TransferSessionState
import com.example.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class SocketTransferManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val _sessionState = MutableStateFlow<TransferSessionState>(TransferSessionState.Idle)
    val sessionState: StateFlow<TransferSessionState> = _sessionState.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<TransferRecord>>(emptyList())
    val transferHistory: StateFlow<List<TransferRecord>> = _transferHistory.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var transferJob: Job? = null

    companion object {
        private const val MAGIC_HEADER = 0x53484152 // 'SHAR'
        private const val ACK_BYTE: Byte = 0x06
        private const val CHUNK_SIZE = 64 * 1024 // 64 KB
    }

    /**
     * Starts listening as a receiver on the local network.
     */
    fun startReceiving(port: Int = NetworkUtils.DEFAULT_PORT) {
        stopAll()
        val localIp = NetworkUtils.getLocalIpAddress()
        _sessionState.value = TransferSessionState.Listening(localIp, port)

        transferJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    activeSocket = socket
                    handleIncomingTransfer(socket)
                }
            } catch (e: Exception) {
                if (isActive) {
                    _sessionState.value = TransferSessionState.Error("Server error: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                closeQuietly(serverSocket)
                serverSocket = null
            }
        }
    }

    private suspend fun handleIncomingTransfer(socket: Socket) = withContext(Dispatchers.IO) {
        val peerAddress = socket.inetAddress?.hostAddress ?: "Unknown"
        var dataIn: DataInputStream? = null
        var dataOut: DataOutputStream? = null

        val receivedDir = getReceiveDirectory()

        try {
            dataIn = DataInputStream(BufferedInputStream(socket.getInputStream()))
            dataOut = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val magic = dataIn.readInt()
            if (magic != MAGIC_HEADER) {
                dataOut.writeByte(0xFF) // NACK
                dataOut.flush()
                _sessionState.value = TransferSessionState.Error("Invalid protocol handshake from peer")
                return@withContext
            }

            val fileCount = dataIn.readInt()
            val completedNames = mutableListOf<String>()
            var totalBytesReceivedAcrossFiles = 0L

            for (index in 0 until fileCount) {
                val nameLength = dataIn.readInt()
                val nameBytes = ByteArray(nameLength)
                dataIn.readFully(nameBytes)
                val fileName = String(nameBytes, Charsets.UTF_8)
                val fileSize = dataIn.readLong()

                val destFile = getUniqueFile(receivedDir, fileName)
                var fileBytesReceived = 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var currentSpeed = 0L

                FileOutputStream(destFile).use { fileOut ->
                    val buffer = ByteArray(CHUNK_SIZE)
                    while (fileBytesReceived < fileSize) {
                        val toRead = Math.min(buffer.size.toLong(), fileSize - fileBytesReceived).toInt()
                        val bytesRead = dataIn.read(buffer, 0, toRead)
                        if (bytesRead == -1) break

                        fileOut.write(buffer, 0, bytesRead)
                        fileBytesReceived += bytesRead
                        totalBytesReceivedAcrossFiles += bytesRead
                        bytesSinceLastCalc += bytesRead

                        val now = System.currentTimeMillis()
                        val diff = now - lastSpeedCalcTime
                        if (diff >= 500) {
                            currentSpeed = (bytesSinceLastCalc * 1000L) / diff
                            lastSpeedCalcTime = now
                            bytesSinceLastCalc = 0L

                            _sessionState.value = TransferSessionState.Transferring(
                                isOutgoing = false,
                                peerIp = peerAddress,
                                currentFileName = fileName,
                                currentFileIndex = index + 1,
                                totalFiles = fileCount,
                                currentFileTransferred = fileBytesReceived,
                                currentFileSize = fileSize,
                                totalBytesTransferred = totalBytesReceivedAcrossFiles,
                                totalBytesSize = totalBytesReceivedAcrossFiles + (fileSize - fileBytesReceived),
                                speedBytesPerSec = currentSpeed,
                                completedFiles = completedNames
                            )
                        }
                    }
                    fileOut.flush()
                }

                // Acknowledge file completion
                dataOut.writeByte(ACK_BYTE.toInt())
                dataOut.flush()

                completedNames.add(destFile.name)
                addHistoryRecord(
                    fileName = destFile.name,
                    fileSize = fileSize,
                    isOutgoing = false,
                    status = TransferStatus.COMPLETED,
                    filePath = destFile.absolutePath
                )
            }

            _sessionState.value = TransferSessionState.Completed(
                totalFiles = fileCount,
                totalBytes = totalBytesReceivedAcrossFiles,
                isOutgoing = false,
                fileNames = completedNames
            )

        } catch (e: Exception) {
            _sessionState.value = TransferSessionState.Error("Receive failed: ${e.localizedMessage ?: "Connection interrupted"}")
        } finally {
            closeQuietly(dataIn)
            closeQuietly(dataOut)
            closeQuietly(socket)
        }
    }

    /**
     * Connects to the receiver and streams the selected files.
     */
    fun startSending(receiverIp: String, files: List<FileItem>, port: Int = NetworkUtils.DEFAULT_PORT) {
        stopAll()
        if (files.isEmpty()) {
            _sessionState.value = TransferSessionState.Error("No files selected to send")
            return
        }

        _sessionState.value = TransferSessionState.Connecting(receiverIp, port)

        transferJob = scope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            var dataIn: DataInputStream? = null
            var dataOut: DataOutputStream? = null

            val totalBytesAllFiles = files.sumOf { it.size }
            var totalBytesSentSoFar = 0L
            val completedNames = mutableListOf<String>()

            try {
                socket = Socket().apply {
                    connect(InetSocketAddress(receiverIp, port), 10000)
                    soTimeout = 30000
                }
                activeSocket = socket

                dataOut = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                dataIn = DataInputStream(BufferedInputStream(socket.getInputStream()))

                // Handshake
                dataOut.writeInt(MAGIC_HEADER)
                dataOut.writeInt(files.size)
                dataOut.flush()

                for ((index, fileItem) in files.withIndex()) {
                    val nameBytes = fileItem.name.toByteArray(Charsets.UTF_8)
                    dataOut.writeInt(nameBytes.size)
                    dataOut.write(nameBytes)
                    dataOut.writeLong(fileItem.size)
                    dataOut.flush()

                    var inputStream: InputStream? = null
                    try {
                        inputStream = context.contentResolver.openInputStream(fileItem.uri)
                            ?: File(fileItem.uri.path ?: "").inputStream()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (inputStream == null) {
                        throw IllegalStateException("Cannot read file stream: ${fileItem.name}")
                    }

                    inputStream.use { stream ->
                        val buffer = ByteArray(CHUNK_SIZE)
                        var fileSentBytes = 0L
                        var lastSpeedCalcTime = System.currentTimeMillis()
                        var bytesSinceLastCalc = 0L
                        var currentSpeed = 0L

                        while (true) {
                            val read = stream.read(buffer)
                            if (read == -1) break

                            dataOut.write(buffer, 0, read)
                            fileSentBytes += read
                            totalBytesSentSoFar += read
                            bytesSinceLastCalc += read

                            val now = System.currentTimeMillis()
                            val diff = now - lastSpeedCalcTime
                            if (diff >= 500) {
                                currentSpeed = (bytesSinceLastCalc * 1000L) / diff
                                lastSpeedCalcTime = now
                                bytesSinceLastCalc = 0L

                                _sessionState.value = TransferSessionState.Transferring(
                                    isOutgoing = true,
                                    peerIp = receiverIp,
                                    currentFileName = fileItem.name,
                                    currentFileIndex = index + 1,
                                    totalFiles = files.size,
                                    currentFileTransferred = fileSentBytes,
                                    currentFileSize = fileItem.size,
                                    totalBytesTransferred = totalBytesSentSoFar,
                                    totalBytesSize = totalBytesAllFiles,
                                    speedBytesPerSec = currentSpeed,
                                    completedFiles = completedNames
                                )
                            }
                        }
                        dataOut.flush()
                    }

                    // Await receiver ACK
                    val ack = dataIn.readByte()
                    if (ack != ACK_BYTE) {
                        throw IllegalStateException("Peer did not acknowledge file: ${fileItem.name}")
                    }

                    completedNames.add(fileItem.name)
                    addHistoryRecord(
                        fileName = fileItem.name,
                        fileSize = fileItem.size,
                        isOutgoing = true,
                        status = TransferStatus.COMPLETED
                    )
                }

                _sessionState.value = TransferSessionState.Completed(
                    totalFiles = files.size,
                    totalBytes = totalBytesSentSoFar,
                    isOutgoing = true,
                    fileNames = completedNames
                )

            } catch (e: Exception) {
                _sessionState.value = TransferSessionState.Error("Transfer failed: ${e.localizedMessage ?: "Connection error"}")
                addHistoryRecord(
                    fileName = if (files.size == 1) files[0].name else "${files.size} files",
                    fileSize = totalBytesAllFiles,
                    isOutgoing = true,
                    status = TransferStatus.FAILED
                )
            } finally {
                closeQuietly(dataIn)
                closeQuietly(dataOut)
                closeQuietly(socket)
            }
        }
    }

    fun stopAll() {
        transferJob?.cancel()
        transferJob = null
        closeQuietly(serverSocket)
        serverSocket = null
        closeQuietly(activeSocket)
        activeSocket = null
        _sessionState.value = TransferSessionState.Idle
    }

    private fun addHistoryRecord(
        fileName: String,
        fileSize: Long,
        isOutgoing: Boolean,
        status: TransferStatus,
        filePath: String = ""
    ) {
        val record = TransferRecord(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            fileSize = fileSize,
            isOutgoing = isOutgoing,
            status = status,
            timestamp = System.currentTimeMillis(),
            filePath = filePath
        )
        _transferHistory.value = listOf(record) + _transferHistory.value.take(25)
    }

    private fun getReceiveDirectory(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val dir = File(base, "AirShare_Received")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getUniqueFile(directory: File, fileName: String): File {
        var file = File(directory, fileName)
        if (!file.exists()) return file

        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""

        var counter = 1
        while (file.exists()) {
            file = File(directory, "${baseName}_$counter$extension")
            counter++
        }
        return file
    }

    private fun closeQuietly(closeable: java.io.Closeable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {}
    }
}
