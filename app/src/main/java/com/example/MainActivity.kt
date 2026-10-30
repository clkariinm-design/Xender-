package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.FileExplorerScreen
import com.example.ui.HomeScreen
import com.example.ui.ReceiverScreen
import com.example.ui.SenderScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsStateWithLifecycle()
    val currentCategory by viewModel.currentCategory.collectAsStateWithLifecycle()
    val categoryFiles by viewModel.categoryFiles.collectAsStateWithLifecycle()
    val selectedFileIds by viewModel.selectedFileIds.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoadingFiles by viewModel.isLoadingFiles.collectAsStateWithLifecycle()
    val targetReceiverIp by viewModel.targetReceiverIp.collectAsStateWithLifecycle()
    val deviceIpAddress by viewModel.deviceIpAddress.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val transferHistory by viewModel.transferHistory.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        viewModel.setStoragePermissionGranted(granted)
    }

    // Check permissions and ask automatically on startup
    LaunchedEffect(Unit) {
        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.setStoragePermissionGranted(allGranted)
        if (!allGranted) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)

        when (currentScreen) {
            AppScreen.HOME -> {
                HomeScreen(
                    ipAddress = deviceIpAddress,
                    hasStoragePermission = hasStoragePermission,
                    onRequestPermission = { permissionLauncher.launch(permissionsToRequest) },
                    onSendClick = { viewModel.navigateTo(AppScreen.FILE_EXPLORER) },
                    onReceiveClick = { viewModel.navigateTo(AppScreen.RECEIVER) },
                    transferHistory = transferHistory,
                    modifier = contentModifier
                )
            }
            AppScreen.FILE_EXPLORER -> {
                FileExplorerScreen(
                    currentCategory = currentCategory,
                    categoryFiles = categoryFiles,
                    selectedFileIds = selectedFileIds,
                    searchQuery = searchQuery,
                    isLoading = isLoadingFiles,
                    onCategoryChange = { viewModel.setCategory(it) },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onToggleSelect = { viewModel.toggleFileSelection(it) },
                    onSelectAll = { viewModel.selectAllInCurrentCategory() },
                    onClearSelection = { viewModel.clearSelection() },
                    onSendFiles = { ip -> viewModel.startSendingSelectedFiles(ip) },
                    onBack = { viewModel.navigateBack() },
                    modifier = contentModifier
                )
            }
            AppScreen.RECEIVER -> {
                ReceiverScreen(
                    sessionState = sessionState,
                    onBack = { viewModel.navigateBack() },
                    onRestartReceiving = { viewModel.navigateTo(AppScreen.RECEIVER) },
                    modifier = contentModifier
                )
            }
            AppScreen.SENDER -> {
                SenderScreen(
                    sessionState = sessionState,
                    targetReceiverIp = targetReceiverIp,
                    onBack = { viewModel.navigateBack() },
                    onCancel = { viewModel.stopTransfer() },
                    onRetry = {
                        val files = viewModel.getSelectedFiles()
                        if (files.isNotEmpty()) {
                            viewModel.startSendingSelectedFiles(targetReceiverIp)
                        } else {
                            viewModel.navigateTo(AppScreen.FILE_EXPLORER)
                        }
                    },
                    modifier = contentModifier
                )
            }
        }
    }
}
