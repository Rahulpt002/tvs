package com.ntorqnav.bridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ntorqnav.bridge.bluetooth.ConnectionState
import com.ntorqnav.bridge.bridge.NavigationBridge
import com.ntorqnav.bridge.bridge.OperationMode
import com.ntorqnav.bridge.navigation.Maneuver
import com.ntorqnav.bridge.ui.theme.AccentOrange
import com.ntorqnav.bridge.ui.theme.DangerRed
import com.ntorqnav.bridge.ui.theme.PrimaryTeal
import com.ntorqnav.bridge.ui.theme.SecondaryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    bridge: NavigationBridge,
    onNavigateToScan: () -> Unit,
    onNavigateToTest: () -> Unit,
    onNavigateToDev: () -> Unit
) {
    val telemetry by bridge.telemetry.collectAsState()
    val connState by bridge.connectionManager.connectionState.collectAsState()
    val isTvsValidated by bridge.connectionManager.isTvsProtocolValidated.collectAsState()
    val isMapsConnected = bridge.googleMapsProvider.isListenerServiceConnected()

    val lastUpdate = telemetry.lastUpdate

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NTORQ NAV BRIDGE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(
                            text = if (telemetry.operationMode == OperationMode.SIMULATION)
                                "MODE: SIMULATOR (SAFE)" else "MODE: REAL VEHICLE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (telemetry.operationMode == OperationMode.SIMULATION) PrimaryTeal else AccentOrange
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text("Sim", fontSize = 12.sp)
                        Switch(
                            checked = telemetry.operationMode == OperationMode.SIMULATION,
                            onCheckedChange = { isSim ->
                                bridge.setOperationMode(if (isSim) OperationMode.SIMULATION else OperationMode.REAL_VEHICLE)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Grid
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusRow(
                        label = "Bluetooth",
                        statusText = when (connState) {
                            ConnectionState.CONNECTED -> "Connected"
                            ConnectionState.READY -> "Connected & Ready"
                            ConnectionState.CONNECTING -> "Connecting..."
                            ConnectionState.SCANNING -> "Scanning..."
                            else -> "Disconnected"
                        },
                        isActive = connState == ConnectionState.CONNECTED || connState == ConnectionState.READY
                    )
                    StatusRow(
                        label = "TFT Instrument Cluster",
                        statusText = if (telemetry.operationMode == OperationMode.SIMULATION) "Simulator Ready"
                        else if (isTvsValidated) "TVS GATT Ready" else "Waiting GATT validation",
                        isActive = telemetry.operationMode == OperationMode.SIMULATION || isTvsValidated
                    )
                    StatusRow(
                        label = "Navigation Bridge",
                        statusText = if (telemetry.isBridgeRunning) "Active" else "Stopped",
                        isActive = telemetry.isBridgeRunning
                    )
                    StatusRow(
                        label = "Google Maps Provider",
                        statusText = if (isMapsConnected) "Receiving navigation" else "Listener unlinked",
                        isActive = isMapsConnected
                    )
                }
            }

            // Current Navigation Instruction Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "CURRENT INSTRUCTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    if (lastUpdate != null && telemetry.isBridgeRunning) {
                        Icon(
                            imageVector = getManeuverIcon(lastUpdate.maneuver),
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = lastUpdate.maneuver.name.replace('_', ' '),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = lastUpdate.distanceMeters?.let { "$it m" } ?: "--",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = SecondaryGreen
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = lastUpdate.roadName ?: lastUpdate.instruction ?: "Proceed on Route",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = lastUpdate.etaMinutes?.let { "ETA: $it min" } ?: "ETA: --",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No Active Navigation",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Start the bridge and trigger a test or Google Maps route",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToScan,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan Scooter")
                    }

                    Button(
                        onClick = {
                            if (connState == ConnectionState.CONNECTED || connState == ConnectionState.READY) {
                                bridge.connectionManager.disconnect()
                            } else {
                                bridge.connectionManager.connect()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (connState == ConnectionState.CONNECTED || connState == ConnectionState.READY) "Disconnect"
                            else "Connect BLE",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (telemetry.isBridgeRunning) bridge.stopBridge()
                            else bridge.startBridge()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (telemetry.isBridgeRunning) DangerRed else SecondaryGreen
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (telemetry.isBridgeRunning) "Stop Bridge" else "Start Bridge",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToTest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test Nav")
                    }
                }

                Button(
                    onClick = onNavigateToDev,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Developer Mode & Protocol Diagnostics")
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, statusText: String, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isActive) SecondaryGreen else DangerRed)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) SecondaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
