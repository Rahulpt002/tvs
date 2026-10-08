package com.ntorqnav.bridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
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
import com.ntorqnav.bridge.bluetooth.DiscoveredBluetoothDevice
import com.ntorqnav.bridge.bluetooth.DiscoveredGattCharacteristic
import com.ntorqnav.bridge.bluetooth.DiscoveredGattService
import com.ntorqnav.bridge.bluetooth.NtorqConnectionManager
import com.ntorqnav.bridge.tvs.TvsConstants
import com.ntorqnav.bridge.ui.theme.PrimaryTeal
import com.ntorqnav.bridge.ui.theme.SecondaryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothInspectorScreen(
    connectionManager: NtorqConnectionManager
) {
    val isScanning by connectionManager.scanner.isScanning.collectAsState()
    val devices by connectionManager.scanner.discoveredDevices.collectAsState()
    val connState by connectionManager.connectionState.collectAsState()
    val selectedDevice by connectionManager.selectedDevice.collectAsState()
    val services by connectionManager.discoveredServices.collectAsState()
    val isTvsValidated by connectionManager.isTvsProtocolValidated.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Inspector", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (isScanning) {
                        IconButton(onClick = { connectionManager.stopScanning() }) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = PrimaryTeal,
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        IconButton(onClick = { connectionManager.startScanning() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Connection Action Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (connState == ConnectionState.READY || connState == ConnectionState.CONNECTED)
                                    Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = if (connState == ConnectionState.READY) SecondaryGreen else PrimaryTeal
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Status: ${connState.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        if (selectedDevice != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Target: ${selectedDevice?.displayName} (${selectedDevice?.address})",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isTvsValidated) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SecondaryGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "NTORQ TFT GATT Profile Validated!",
                                    color = SecondaryGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (isScanning) connectionManager.stopScanning()
                                    else connectionManager.startScanning()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                            ) {
                                Text(if (isScanning) "Stop Scan" else "Scan Devices", color = Color.Black)
                            }

                            if (selectedDevice != null) {
                                if (connState == ConnectionState.CONNECTED || connState == ConnectionState.READY) {
                                    OutlinedButton(onClick = { connectionManager.disconnect() }) {
                                        Text("Disconnect")
                                    }
                                } else {
                                    Button(
                                        onClick = { connectionManager.connect() },
                                        enabled = connState != ConnectionState.CONNECTING
                                    ) {
                                        Text(if (connState == ConnectionState.CONNECTING) "Connecting..." else "Connect BLE")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Discovered GATT Profile Tree (if connected)
            if (services.isNotEmpty()) {
                item {
                    Text(
                        "GATT Services & Characteristics (${services.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(services) { service ->
                    GattServiceCard(service)
                }
            }

            // Scanned Devices Header
            item {
                Text(
                    "Nearby Bluetooth Devices (${devices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (devices.isEmpty() && isScanning) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Searching for NTORQ & nearby BLE devices...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(devices) { device ->
                DeviceItemCard(
                    device = device,
                    isSelected = selectedDevice?.address == device.address,
                    onSelect = { connectionManager.selectDevice(device) }
                )
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    device: DiscoveredBluetoothDevice,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (device.isNtorqCandidate) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = SecondaryGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "NTORQ",
                                color = SecondaryGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = device.address,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${device.rssi} dBm",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GattServiceCard(service: DiscoveredGattService) {
    val isTvsService = service.uuid == TvsConstants.SERVICE_UUID_NTORQ || service.uuid == TvsConstants.SERVICE_UUID_NTORQ_ALT
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isTvsService) PrimaryTeal.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Service: ${service.uuid}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isTvsService) PrimaryTeal else MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))
            service.characteristics.forEach { charItem ->
                val isWrite = charItem.uuid == TvsConstants.CHAR_WRITE_UUID
                val isNotify = charItem.uuid == TvsConstants.CHAR_NOTIFY_READ_UUID
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Text(
                        text = "└ Characteristic: ${charItem.uuid}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (isWrite || isNotify) SecondaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "   Properties: ${charItem.properties.joinToString(", ")}" +
                                (if (isWrite) " [WRITE TARGET]" else "") +
                                (if (isNotify) " [NOTIFY TARGET]" else ""),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
