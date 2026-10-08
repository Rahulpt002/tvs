package com.ntorqnav.bridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ntorqnav.bridge.bluetooth.ConnectionState
import com.ntorqnav.bridge.bluetooth.NtorqConnectionManager
import com.ntorqnav.bridge.diagnostic.BondingState
import com.ntorqnav.bridge.diagnostic.ComparisonStatus
import com.ntorqnav.bridge.diagnostic.DiagnosticDeviceInfo
import com.ntorqnav.bridge.diagnostic.DiagnosticExporter
import com.ntorqnav.bridge.diagnostic.DiagnosticSessionRecorder
import com.ntorqnav.bridge.diagnostic.FrameClassifier
import com.ntorqnav.bridge.diagnostic.GattVerifier
import com.ntorqnav.bridge.diagnostic.StaticAnalysisComparator
import com.ntorqnav.bridge.tvs.TvsConstants
import com.ntorqnav.bridge.ui.theme.AccentOrange
import com.ntorqnav.bridge.ui.theme.DangerRed
import com.ntorqnav.bridge.ui.theme.PrimaryTeal
import com.ntorqnav.bridge.ui.theme.SecondaryGreen

/**
 * Phase 2 — real NTORQ 150 BLE validation screen.
 *
 * Passive observation only: scan, connect, verify the GATT profile, and record naturally-exposed
 * connection metadata + raw notifications. It never writes to unknown characteristics, never
 * decrypts a frame, and never extracts a key. Export produces a JSON session and a comparison
 * report (`real-device-validation.md`) for off-device analysis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    connectionManager: NtorqConnectionManager,
    recorder: DiagnosticSessionRecorder,
    exporter: DiagnosticExporter,
) {
    val isScanning by connectionManager.scanner.isScanning.collectAsState()
    val devices by connectionManager.scanner.discoveredDevices.collectAsState()
    val connState by connectionManager.connectionState.collectAsState()
    val selectedDevice by connectionManager.selectedDevice.collectAsState()
    val services by connectionManager.discoveredServices.collectAsState()
    val mtu by connectionManager.negotiatedMtu.collectAsState()
    val isRecording by recorder.isRecording.collectAsState()
    val draft by recorder.draft.collectAsState()

    // Feed naturally-observed raw notifications into the recorder while recording.
    LaunchedEffect(Unit) {
        connectionManager.rawNotifications.collect { raw ->
            recorder.recordNotification(raw.characteristicUuid, raw.bytes)
        }
    }
    // Capture GATT discovery + MTU + bond state into the active recording.
    LaunchedEffect(services, mtu, connState) {
        if (recorder.isRecording.value) {
            if (services.isNotEmpty()) recorder.setDiscoveredServices(services)
            mtu?.let { recorder.setMtu(it) }
            recorder.recordEvent(
                com.ntorqnav.bridge.diagnostic.DiagnosticEventType.NOTE,
                connectionState = connState.name,
                detail = "state=${connState.name}",
            )
            val bond = connectionManager.readBondState()
            recorder.setBondingState(runCatching { BondingState.valueOf(bond) }.getOrDefault(BondingState.UNKNOWN))
            selectedDevice?.let {
                recorder.setDevice(
                    DiagnosticDeviceInfo(
                        name = it.name,
                        address = it.address,
                        rssi = it.rssi,
                        advertisedServiceUuids = it.serviceUuids.map { u -> u.toString() },
                        manufacturerData = it.manufacturerData,
                        tvsServiceAdvertised = it.advertisesTvsService,
                    ),
                )
            }
        }
    }

    val gatt = remember(services) { GattVerifier.verify(services) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Real-Device Validation", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = {
                        if (isScanning) connectionManager.stopScanning() else connectionManager.startScanning()
                    }) {
                        if (isScanning) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = PrimaryTeal, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RecorderControls(isRecording, draft, connState, recorder, exporter) }

            item { GattVerificationCard(gatt, mtu, connState) }

            if (draft != null && (draft!!.notifications.isNotEmpty() || !isRecording)) {
                item { ComparisonCard(draft!!) }
            }

            item {
                SectionHeader("Prioritised devices (${devices.size})")
            }
            items(devices) { device ->
                DiagnosticDeviceCard(
                    device = device,
                    isSelected = selectedDevice?.address == device.address,
                    onSelect = { connectionManager.selectDevice(device) },
                    onConnect = { connectionManager.connect(device.address) },
                )
            }

            draft?.notifications?.takeLast(20)?.reversed()?.let { recent ->
                if (recent.isNotEmpty()) {
                    item { SectionHeader("Live notifications (${draft!!.notifications.size})") }
                    items(recent) { n ->
                        val cat = FrameClassifier.classify(
                            FrameClassifier.hexToBytes(n.hexBytes),
                            characteristicUuid = n.characteristicUuid,
                        ).category
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                "+${n.deltaMsFromPrev}ms  [${n.length}B]  $cat",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(n.hexBytes, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecorderControls(
    isRecording: Boolean,
    draft: com.ntorqnav.bridge.diagnostic.DiagnosticSession?,
    connState: ConnectionState,
    recorder: DiagnosticSessionRecorder,
    exporter: DiagnosticExporter,
) {
    var lastExportPath by remember { mutableStateOf<String?>(null) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = if (isRecording) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isRecording) "Recording session" else "Session recorder",
                    fontWeight = FontWeight.Bold,
                )
            }
            draft?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${it.notifications.size} notifications · ${it.events.size} events · ${it.services.size} services",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isRecording) {
                    Button(
                        onClick = { recorder.startRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    ) { Text("Start Recording", color = Color.White) }
                } else {
                    Button(
                        onClick = { recorder.stopRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop", color = Color.Black)
                    }
                }
                draft?.let { session ->
                    OutlinedButton(onClick = {
                        val json = recorder.export(session)
                        val jsonFile = exporter.writeSessionJson(session, json)
                        val report = recorder.generateReport(session)
                        exporter.writeValidationReport(session, report)
                        lastExportPath = jsonFile.parentFile?.absolutePath
                        exporter.share(jsonFile, "application/json")
                    }, enabled = !isRecording) { Text("Export") }
                }
            }
            lastExportPath?.let {
                Spacer(Modifier.height(8.dp))
                Text("Exported to: $it", fontSize = 10.sp, color = SecondaryGreen, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun GattVerificationCard(
    gatt: com.ntorqnav.bridge.diagnostic.GattVerificationResult,
    mtu: Int?,
    connState: ConnectionState,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("GATT Verification", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            VerifyRow("Connection", connState.name, connState == ConnectionState.READY || connState == ConnectionState.CONNECTED)
            VerifyRow("Expected TVS service", if (gatt.expectedServicePresent) "PRESENT" else "NOT OBSERVED", gatt.expectedServicePresent)
            VerifyRow("Write 0x5352", if (gatt.writeChar5352Present) "PRESENT" else "NOT OBSERVED", gatt.writeChar5352Present)
            VerifyRow("Notify 0x5354", if (gatt.notifyChar5354Present) "PRESENT" else "NOT OBSERVED", gatt.notifyChar5354Present)
            VerifyRow("MTU", mtu?.toString() ?: "NOT OBSERVED", mtu != null)
        }
    }
}

@Composable
private fun VerifyRow(label: String, value: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (ok) SecondaryGreen else AccentOrange)
    }
}

@Composable
private fun ComparisonCard(session: com.ntorqnav.bridge.diagnostic.DiagnosticSession) {
    val comparisons = remember(session) { StaticAnalysisComparator.compare(session) }
    val overall = remember(comparisons) { StaticAnalysisComparator.overallConfidence(comparisons) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Static vs Observed", fontWeight = FontWeight.Bold)
                Text("Confidence: $overall", fontWeight = FontWeight.Bold, color = PrimaryTeal)
            }
            Spacer(Modifier.height(8.dp))
            comparisons.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c.item, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(
                        c.status.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (c.status) {
                            ComparisonStatus.MATCH -> SecondaryGreen
                            ComparisonStatus.MISMATCH -> DangerRed
                            ComparisonStatus.NOT_OBSERVED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun DiagnosticDeviceCard(
    device: com.ntorqnav.bridge.bluetooth.DiscoveredBluetoothDevice,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (device.advertisesTvsService) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = PrimaryTeal.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text("TVS SVC", color = PrimaryTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    } else if (device.isNtorqCandidate) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = SecondaryGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text("CANDIDATE", color = SecondaryGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text("${device.rssi} dBm", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(device.address, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (device.manufacturerData.isNotEmpty()) {
                device.manufacturerData.forEach { (id, payload) ->
                    Text("mfr $id: $payload", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isSelected) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)) {
                    Text("Connect & Verify", color = Color.Black)
                }
            }
        }
    }
}
