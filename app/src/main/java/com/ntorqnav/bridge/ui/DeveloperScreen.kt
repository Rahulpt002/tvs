package com.ntorqnav.bridge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ntorqnav.bridge.bridge.NavigationBridge
import com.ntorqnav.bridge.logging.AppLogger
import com.ntorqnav.bridge.replay.PacketReplaySystem
import com.ntorqnav.bridge.simulator.TvsProtocolSimulator
import com.ntorqnav.bridge.tvs.TvsConstants
import com.ntorqnav.bridge.ui.theme.AccentOrange
import com.ntorqnav.bridge.ui.theme.DangerRed
import com.ntorqnav.bridge.ui.theme.PrimaryTeal
import com.ntorqnav.bridge.ui.theme.SecondaryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    bridge: NavigationBridge,
    simulator: TvsProtocolSimulator,
    replaySystem: PacketReplaySystem
) {
    val context = LocalContext.current
    val telemetry by bridge.telemetry.collectAsState()
    val connState by bridge.connectionManager.connectionState.collectAsState()
    val selectedDevice by bridge.connectionManager.selectedDevice.collectAsState()
    val services by bridge.connectionManager.discoveredServices.collectAsState()
    val logs by AppLogger.entries.collectAsState()

    val simDisplay by simulator.displayState.collectAsState()
    val isRecording by replaySystem.isRecording.collectAsState()
    val recordedPackets by replaySystem.recordedPackets.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Diagnostics", "Simulator", "Replay", "Logs")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer & Protocol Mode", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostic Logs", AppLogger.export()))
                        Toast.makeText(context, "Diagnostic logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Export Logs")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = activeTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = { Text(title, fontSize = 13.sp) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (activeTab) {
                    0 -> DiagnosticsTab(bridge, connState, selectedDevice, services, telemetry)
                    1 -> SimulatorTab(simulator, simDisplay)
                    2 -> ReplayTab(replaySystem, isRecording, recordedPackets)
                    3 -> LogsTab(logs)
                }
            }
        }
    }
}

@Composable
fun DiagnosticsTab(
    bridge: NavigationBridge,
    connState: com.ntorqnav.bridge.bluetooth.ConnectionState,
    selectedDevice: com.ntorqnav.bridge.bluetooth.DiscoveredBluetoothDevice?,
    services: List<com.ntorqnav.bridge.bluetooth.DiscoveredGattService>,
    telemetry: com.ntorqnav.bridge.bridge.BridgeTelemetry
) {
    // Bluetooth Section
    DiagnosticCard(title = "Bluetooth Diagnostic State") {
        DiagItem("Device", selectedDevice?.displayName ?: "None Selected")
        DiagItem("Address", selectedDevice?.address ?: "--")
        DiagItem("RSSI", selectedDevice?.let { "${it.rssi} dBm" } ?: "--")
        DiagItem("Connection", connState.name)
        DiagItem("Services Discovered", "${services.size}")
        DiagItem("Characteristics Count", "${services.flatMap { it.characteristics }.size}")
        DiagItem("Write Whitelist Active", "${TvsConstants.ALLOWED_WRITE_CHARACTERISTICS.size} allowed")
    }

    // Navigation Section
    DiagnosticCard(title = "Navigation Provider State") {
        DiagItem("Active Provider", telemetry.activeProviderName)
        DiagItem("Bridge State", telemetry.bridgeState.name)
        DiagItem("Last Instruction", telemetry.lastUpdate?.instruction ?: "--")
        DiagItem("Maneuver", telemetry.lastUpdate?.maneuver?.name ?: "--")
        DiagItem("Distance", telemetry.lastUpdate?.distanceMeters?.let { "$it m" } ?: "--")
        DiagItem("Road Name", telemetry.lastUpdate?.roadName ?: "--")
        DiagItem("ETA", telemetry.lastUpdate?.etaMinutes?.let { "$it min" } ?: "--")
        DiagItem("Timestamp", telemetry.lastUpdate?.timestamp?.toString() ?: "--")
    }

    // Protocol Section
    DiagnosticCard(title = "Protocol & Safety Interlock") {
        DiagItem("Protected Transport", bridge.protectedTransport.transportName)
        DiagItem("Safety Interlock Engaged", "${bridge.protectedTransport.isSafetyInterlockEngaged}")
        DiagItem("TX Packets Total", "${telemetry.txPacketCount}")
        DiagItem("RX Packets Total", "${telemetry.rxPacketCount}")
        DiagItem("Last TX Result", telemetry.lastTransmissionResult)
    }
}

@Composable
fun SimulatorTab(
    simulator: TvsProtocolSimulator,
    simDisplay: com.ntorqnav.bridge.simulator.SimulatedClusterDisplay
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("NTORQ TFT Simulated Instrument Display", fontWeight = FontWeight.Bold, color = PrimaryTeal)
            Spacer(Modifier.height(10.dp))

            DiagItem("Display Maneuver", simDisplay.maneuverName)
            DiagItem("Display Pictogram ID", "${simDisplay.pictogramId}")
            DiagItem("Distance to Turn", "${simDisplay.distanceMeters} m")
            DiagItem("Road / Instruction", simDisplay.roadName.ifBlank { "--" })
            DiagItem("ETA Minutes", "${simDisplay.etaMinutes} min")
            DiagItem("Total Route Distance", "${simDisplay.totalDistanceMeters} m")
            DiagItem("Nav Active Flag", "${simDisplay.isNavigationActive}")
            DiagItem("Packets Received (RX)", "${simDisplay.rxPacketCount}")
            DiagItem("Last Packet Hex", simDisplay.lastPacketHex.ifBlank { "None" })

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { simulator.emitMockSpeedometerFrame(speedKmph = 42, fuelPercent = 75, odoKm = 1420) },
                colors = ButtonDefaults.buttonColors(containerColor = SecondaryGreen)
            ) {
                Text("Emit Simulated Speedometer (42 km/h)", color = Color.Black)
            }
        }
    }
}

@Composable
fun ReplayTab(
    replaySystem: PacketReplaySystem,
    isRecording: Boolean,
    recordedPackets: List<com.ntorqnav.bridge.replay.ReplayPacket>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Packet Recording & Replay Engine", fontWeight = FontWeight.Bold, color = PrimaryTeal)
            Spacer(Modifier.height(8.dp))
            Text("Packets in active session: ${recordedPackets.size}", fontSize = 14.sp)

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (isRecording) replaySystem.stopRecording()
                        else replaySystem.startRecording()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) DangerRed else PrimaryTeal
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isRecording) "Stop Capture" else "Record Packets", color = Color.Black)
                }
            }

            Spacer(Modifier.height(12.dp))
            recordedPackets.takeLast(10).forEach { pkt ->
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("[${pkt.direction}] ${pkt.description}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(pkt.hexPayload, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun LogsTab(logs: List<com.ntorqnav.bridge.logging.LogEntry>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Log Ring Buffer (${logs.size})", fontWeight = FontWeight.Bold)
                IconButton(onClick = { AppLogger.clear() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))
            logs.takeLast(50).reversed().forEach { entry ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = "[${entry.tag.name}] ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when (entry.tag) {
                            com.ntorqnav.bridge.logging.LogTag.BLE -> PrimaryTeal
                            com.ntorqnav.bridge.logging.LogTag.PROTOCOL -> SecondaryGreen
                            com.ntorqnav.bridge.logging.LogTag.SAFETY -> AccentOrange
                            com.ntorqnav.bridge.logging.LogTag.ERROR -> DangerRed
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = entry.message,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = PrimaryTeal, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
fun DiagItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}
