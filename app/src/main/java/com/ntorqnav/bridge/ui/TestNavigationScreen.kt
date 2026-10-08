package com.ntorqnav.bridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ntorqnav.bridge.bridge.NavigationBridge
import com.ntorqnav.bridge.navigation.Maneuver
import com.ntorqnav.bridge.ui.theme.AccentOrange
import com.ntorqnav.bridge.ui.theme.PrimaryTeal
import com.ntorqnav.bridge.ui.theme.SecondaryGreen

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TestNavigationScreen(
    bridge: NavigationBridge
) {
    var selectedManeuver by remember { mutableStateOf(Maneuver.LEFT) }
    var selectedDistance by remember { mutableIntStateOf(250) }
    var selectedRoad by remember { mutableStateOf("MG Road") }
    var customRoadText by remember { mutableStateOf("") }
    var etaMinutes by remember { mutableIntStateOf(12) }

    val telemetry by bridge.telemetry.collectAsState()
    val isBridgeRunning = telemetry.isBridgeRunning

    val distances = listOf(50, 100, 250, 500, 1000)
    val roads = listOf("MG Road", "Beach Road", "NH 66", "100ft Ring Road", "Indiranagar")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test Navigation Mode", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            // Live Preview Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current Simulated Instruction", style = MaterialTheme.typography.labelMedium, color = PrimaryTeal)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = getManeuverIcon(selectedManeuver),
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = selectedManeuver.name.replace('_', ' '),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$selectedDistance m  •  ETA: $etaMinutes min",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (customRoadText.isNotBlank()) customRoadText else selectedRoad,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SecondaryGreen
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val road = if (customRoadText.isNotBlank()) customRoadText else selectedRoad
                                bridge.fakeProvider.sendManualUpdate(
                                    maneuver = selectedManeuver,
                                    distanceMeters = selectedDistance,
                                    roadName = road,
                                    etaMinutes = etaMinutes
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Send Test Packet", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (bridge.fakeProvider.isRunning()) {
                                    bridge.fakeProvider.stopSimulation()
                                } else {
                                    bridge.fakeProvider.startSimulation()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (bridge.fakeProvider.isRunning()) "Stop Loop" else "Auto Cycle Loop", color = Color.Black)
                        }
                    }
                }
            }

            // Maneuver Selection
            Text("Maneuver Direction", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Maneuver.values().filter { it != Maneuver.UNKNOWN }.forEach { m ->
                    FilterChip(
                        selected = selectedManeuver == m,
                        onClick = { selectedManeuver = m },
                        label = { Text(m.name.replace('_', ' ')) },
                        leadingIcon = {
                            Icon(getManeuverIcon(m), contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            // Distance Selection
            Text("Distance Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                distances.forEach { dist ->
                    FilterChip(
                        selected = selectedDistance == dist,
                        onClick = { selectedDistance = dist },
                        label = { Text(if (dist >= 1000) "${dist / 1000} km" else "$dist m") }
                    )
                }
            }

            // Road Selection
            Text("Road Name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                roads.forEach { r ->
                    FilterChip(
                        selected = selectedRoad == r && customRoadText.isBlank(),
                        onClick = {
                            selectedRoad = r
                            customRoadText = ""
                        },
                        label = { Text(r) }
                    )
                }
            }

            OutlinedTextField(
                value = customRoadText,
                onValueChange = { customRoadText = it },
                label = { Text("Or enter custom road name (max 17 chars)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ETA Slider
            Text("ETA: $etaMinutes minutes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Slider(
                value = etaMinutes.toFloat(),
                onValueChange = { etaMinutes = it.toInt() },
                valueRange = 1f..60f,
                steps = 59
            )
        }
    }
}

fun getManeuverIcon(maneuver: Maneuver) = when (maneuver) {
    Maneuver.LEFT -> Icons.Default.ArrowBack
    Maneuver.RIGHT -> Icons.Default.ArrowForward
    Maneuver.STRAIGHT -> Icons.Default.ArrowUpward
    Maneuver.SLIGHT_LEFT -> Icons.Default.NorthWest
    Maneuver.SLIGHT_RIGHT -> Icons.Default.NorthEast
    Maneuver.U_TURN -> Icons.Default.Undo
    Maneuver.ROUNDABOUT -> Icons.Default.ChangeCircle
    Maneuver.DESTINATION -> Icons.Default.Flag
    Maneuver.UNKNOWN -> Icons.Default.Navigation
}
