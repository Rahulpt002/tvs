package com.ntorqnav.bridge.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.ntorqnav.bridge.bluetooth.NtorqConnectionManager
import com.ntorqnav.bridge.bridge.NavigationBridge
import com.ntorqnav.bridge.diagnostic.DiagnosticExporter
import com.ntorqnav.bridge.diagnostic.DiagnosticSessionRecorder
import com.ntorqnav.bridge.logging.AppLogger
import com.ntorqnav.bridge.replay.PacketReplaySystem
import com.ntorqnav.bridge.simulator.TvsProtocolSimulator
import com.ntorqnav.bridge.ui.theme.NtorqNavTheme

class MainActivity : ComponentActivity() {

    private lateinit var connectionManager: NtorqConnectionManager
    private lateinit var simulator: TvsProtocolSimulator
    private lateinit var replaySystem: PacketReplaySystem
    private lateinit var bridge: NavigationBridge
    private lateinit var diagnosticRecorder: DiagnosticSessionRecorder
    private lateinit var diagnosticExporter: DiagnosticExporter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            AppLogger.ble("All Bluetooth and Location permissions granted")
        } else {
            AppLogger.error("Some Bluetooth or Location permissions were denied: $permissions")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionManager = NtorqConnectionManager(this)
        simulator = TvsProtocolSimulator()
        replaySystem = PacketReplaySystem()
        bridge = NavigationBridge(connectionManager, simulator, replaySystem)
        diagnosticRecorder = DiagnosticSessionRecorder()
        diagnosticExporter = DiagnosticExporter(applicationContext)

        checkAndRequestPermissions()

        setContent {
            NtorqNavTheme {
                var selectedTabIndex by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                label = { Text("Dashboard") }
                            )
                            NavigationBarItem(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                                label = { Text("Inspector") }
                            )
                            NavigationBarItem(
                                selected = selectedTabIndex == 2,
                                onClick = { selectedTabIndex = 2 },
                                icon = { Icon(Icons.Default.Directions, contentDescription = null) },
                                label = { Text("Test Nav") }
                            )
                            NavigationBarItem(
                                selected = selectedTabIndex == 3,
                                onClick = { selectedTabIndex = 3 },
                                icon = { Icon(Icons.Default.Biotech, contentDescription = null) },
                                label = { Text("Validate") }
                            )
                            NavigationBarItem(
                                selected = selectedTabIndex == 4,
                                onClick = { selectedTabIndex = 4 },
                                icon = { Icon(Icons.Default.Code, contentDescription = null) },
                                label = { Text("Dev Mode") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (selectedTabIndex) {
                            0 -> DashboardScreen(
                                bridge = bridge,
                                onNavigateToScan = { selectedTabIndex = 1 },
                                onNavigateToTest = { selectedTabIndex = 2 },
                                onNavigateToDev = { selectedTabIndex = 4 }
                            )
                            1 -> BluetoothInspectorScreen(connectionManager = connectionManager)
                            2 -> TestNavigationScreen(bridge = bridge)
                            3 -> DiagnosticScreen(
                                connectionManager = connectionManager,
                                recorder = diagnosticRecorder,
                                exporter = diagnosticExporter
                            )
                            4 -> DeveloperScreen(bridge = bridge, simulator = simulator, replaySystem = replaySystem)
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    fun promptNotificationAccess() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
}
