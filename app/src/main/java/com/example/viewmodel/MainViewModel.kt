package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FileRepository
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.TransferRecord
import com.example.model.TransferSessionState
import com.example.network.NetworkUtils
import com.example.network.SocketTransferManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    FILE_EXPLORER,
    RECEIVER,
    SENDER
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository(application)
    val transferManager = SocketTransferManager(application, viewModelScope)

    // Current Screen
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Permissions State
    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    // File Explorer State
    private val _currentCategory = MutableStateFlow(FileCategory.PHOTOS)
    val currentCategory: StateFlow<FileCategory> = _currentCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFiles = MutableStateFlow<Map<FileCategory, List<FileItem>>>(emptyMap())
    val categoryFiles: StateFlow<Map<FileCategory, List<FileItem>>> = _categoryFiles.asStateFlow()

    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds: StateFlow<Set<String>> = _selectedFileIds.asStateFlow()

    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    // Connection Info for Sender
    private val _targetReceiverIp = MutableStateFlow("")
    val targetReceiverIp: StateFlow<String> = _targetReceiverIp.asStateFlow()

    // Device IP and Wi-Fi state
    private val _deviceIpAddress = MutableStateFlow(NetworkUtils.getLocalIpAddress())
    val deviceIpAddress: StateFlow<String> = _deviceIpAddress.asStateFlow()

    val sessionState: StateFlow<TransferSessionState> = transferManager.sessionState
    val transferHistory: StateFlow<List<TransferRecord>> = transferManager.transferHistory

    init {
        refreshNetworkInfo()
    }

    fun setStoragePermissionGranted(granted: Boolean) {
        _hasStoragePermission.value = granted
        if (granted) {
            loadFilesForCurrentCategory()
        }
    }

    fun refreshNetworkInfo() {
        _deviceIpAddress.value = NetworkUtils.getLocalIpAddress()
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        refreshNetworkInfo()
        if (screen == AppScreen.FILE_EXPLORER && _categoryFiles.value.isEmpty()) {
            loadFilesForCurrentCategory()
        } else if (screen == AppScreen.RECEIVER) {
            transferManager.startReceiving()
        }
    }

    fun navigateBack(): Boolean {
        return if (_currentScreen.value != AppScreen.HOME) {
            if (_currentScreen.value == AppScreen.RECEIVER || _currentScreen.value == AppScreen.SENDER) {
                transferManager.stopAll()
            }
            _currentScreen.value = AppScreen.HOME
            true
        } else {
            false
        }
    }

    fun setCategory(category: FileCategory) {
        _currentCategory.value = category
        if (!_categoryFiles.value.containsKey(category)) {
            loadFilesForCurrentCategory()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTargetReceiverIp(ip: String) {
        _targetReceiverIp.value = ip
    }

    fun toggleFileSelection(fileId: String) {
        val current = _selectedFileIds.value.toMutableSet()
        if (current.contains(fileId)) {
            current.remove(fileId)
        } else {
            current.add(fileId)
        }
        _selectedFileIds.value = current
    }

    fun selectAllInCurrentCategory() {
        val currentCategoryItems = _categoryFiles.value[_currentCategory.value] ?: emptyList()
        val allIds = currentCategoryItems.map { it.id }.toSet()
        _selectedFileIds.value = _selectedFileIds.value + allIds
    }

    fun clearSelection() {
        _selectedFileIds.value = emptySet()
    }

    fun getSelectedFiles(): List<FileItem> {
        val selectedIds = _selectedFileIds.value
        return _categoryFiles.value.values.flatten().filter { selectedIds.contains(it.id) }
    }

    fun loadFilesForCurrentCategory() {
        viewModelScope.launch {
            _isLoadingFiles.value = true
            try {
                val category = _currentCategory.value
                val files = repository.loadCategoryFiles(category)
                val updatedMap = _categoryFiles.value.toMutableMap()
                updatedMap[category] = files
                _categoryFiles.value = updatedMap

                // Preload remaining categories in background
                FileCategory.entries.forEach { cat ->
                    if (cat != category && !updatedMap.containsKey(cat)) {
                        updatedMap[cat] = repository.loadCategoryFiles(cat)
                    }
                }
                _categoryFiles.value = updatedMap
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingFiles.value = false
            }
        }
    }

    fun startSendingSelectedFiles(targetIp: String) {
        val files = getSelectedFiles()
        if (files.isNotEmpty() && targetIp.isNotBlank()) {
            _targetReceiverIp.value = targetIp
            _currentScreen.value = AppScreen.SENDER
            transferManager.startSending(targetIp.trim(), files)
        }
    }

    fun stopTransfer() {
        transferManager.stopAll()
    }
}
