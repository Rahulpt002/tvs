package com.ntorqnav.bridge.bridge

import com.ntorqnav.bridge.bluetooth.ConnectionState
import com.ntorqnav.bridge.bluetooth.NtorqConnectionManager
import com.ntorqnav.bridge.logging.AppLogger
import com.ntorqnav.bridge.navigation.FakeNavigationProvider
import com.ntorqnav.bridge.navigation.GoogleMapsNotificationProvider
import com.ntorqnav.bridge.navigation.NavigationProvider
import com.ntorqnav.bridge.navigation.NavigationUpdate
import com.ntorqnav.bridge.replay.PacketDirection
import com.ntorqnav.bridge.replay.PacketReplaySystem
import com.ntorqnav.bridge.simulator.TvsProtocolSimulator
import com.ntorqnav.bridge.tvs.DiagnosticSafeProtectedTransport
import com.ntorqnav.bridge.tvs.ProtectedTransmissionResult
import com.ntorqnav.bridge.tvs.TvsConstants
import com.ntorqnav.bridge.tvs.TvsNavigationProtocol
import com.ntorqnav.bridge.tvs.TvsNavigationProtocolImpl
import com.ntorqnav.bridge.tvs.TvsProtectedTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BridgeState {
    IDLE,
    CONNECTING,
    READY,
    NAVIGATION_STARTING,
    NAVIGATING,
    NAVIGATION_UPDATING,
    NAVIGATION_FINISHED,
    ERROR
}

enum class OperationMode {
    SIMULATION,
    REAL_VEHICLE
}

data class BridgeTelemetry(
    val bridgeState: BridgeState = BridgeState.IDLE,
    val operationMode: OperationMode = OperationMode.SIMULATION,
    val activeProviderName: String = "None",
    val isBridgeRunning: Boolean = false,
    val lastUpdate: NavigationUpdate? = null,
    val txPacketCount: Int = 0,
    val rxPacketCount: Int = 0,
    val lastTransmissionResult: String = "None"
)

class NavigationBridge(
    val connectionManager: NtorqConnectionManager,
    val simulator: TvsProtocolSimulator,
    val replaySystem: PacketReplaySystem,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    val googleMapsProvider = GoogleMapsNotificationProvider()
    val fakeProvider = FakeNavigationProvider()

    private var activeProvider: NavigationProvider = fakeProvider

    private val _telemetry = MutableStateFlow(BridgeTelemetry())
    val telemetry: StateFlow<BridgeTelemetry> = _telemetry.asStateFlow()

    private var navigationJob: Job? = null
    private var connectionObserverJob: Job? = null

    // By default, protected transport with physical writes gated for safety
    var protectedTransport: TvsProtectedTransport = DiagnosticSafeProtectedTransport(
        underlyingTransport = connectionManager.transport,
        allowPhysicalTransmission = false
    )
        private set

    var protocol: TvsNavigationProtocol = TvsNavigationProtocolImpl(protectedTransport)
        private set

    init {
        observeConnectionState()
    }

    fun setOperationMode(mode: OperationMode) {
        _telemetry.update { it.copy(operationMode = mode) }
        AppLogger.bridge("Operation mode changed to $mode")

        // In simulation mode, transport routes to in-memory simulator
        val transport = if (mode == OperationMode.SIMULATION) {
            DiagnosticSafeProtectedTransport(underlyingTransport = simulator.fakeTransport, allowPhysicalTransmission = true)
        } else {
            DiagnosticSafeProtectedTransport(underlyingTransport = connectionManager.transport, allowPhysicalTransmission = false)
        }
        protectedTransport = transport
        protocol = TvsNavigationProtocolImpl(transport)
    }

    fun setProvider(provider: NavigationProvider) {
        if (_telemetry.value.isBridgeRunning) {
            stopBridge()
        }
        activeProvider = provider
        _telemetry.update { it.copy(activeProviderName = provider.name) }
        AppLogger.bridge("Navigation provider switched to ${provider.name}")
    }

    fun startBridge() {
        if (_telemetry.value.isBridgeRunning) return

        AppLogger.bridge("Starting Navigation Bridge...")
        _telemetry.update { it.copy(isBridgeRunning = true, bridgeState = BridgeState.NAVIGATION_STARTING) }

        activeProvider.start()

        navigationJob?.cancel()
        navigationJob = scope.launch {
            protocol.startNavigation()
            _telemetry.update { it.copy(bridgeState = BridgeState.NAVIGATING) }

            activeProvider.observeUpdates().collect { update ->
                handleIncomingNavigationUpdate(update)
            }
        }
    }

    fun stopBridge() {
        if (!_telemetry.value.isBridgeRunning) return

        AppLogger.bridge("Stopping Navigation Bridge...")
        navigationJob?.cancel()
        navigationJob = null

        activeProvider.stop()

        scope.launch {
            protocol.stopNavigation()
            _telemetry.update {
                it.copy(
                    isBridgeRunning = false,
                    bridgeState = if (connectionManager.connectionState.value == ConnectionState.READY) BridgeState.READY else BridgeState.IDLE
                )
            }
        }
    }

    private suspend fun handleIncomingNavigationUpdate(update: NavigationUpdate) {
        _telemetry.update { it.copy(bridgeState = BridgeState.NAVIGATION_UPDATING, lastUpdate = update) }

        val result = protocol.sendNavigationUpdate(update)

        val resultSummary = when (result) {
            is ProtectedTransmissionResult.Success -> "Success: ${result.framesTransmitted} frames (${result.bytesTotal}B)"
            is ProtectedTransmissionResult.BlockedBySafetyInterlock -> "Blocked: ${result.reason}"
            is ProtectedTransmissionResult.Error -> "Error: ${result.error.message}"
        }

        // Record for replay if recording
        val (frameA, frameB) = com.ntorqnav.bridge.tvs.TvsPacketEncoder.encodeNavigationFrames(update)
        replaySystem.recordPacket(PacketDirection.TX, TvsConstants.CHAR_WRITE_UUID, frameB, "Frame B: ${update.instruction ?: update.roadName}")
        replaySystem.recordPacket(PacketDirection.TX, TvsConstants.CHAR_WRITE_UUID, frameA, "Frame A: ${update.maneuver.name} ${update.distanceMeters}m")

        _telemetry.update {
            it.copy(
                bridgeState = BridgeState.NAVIGATING,
                txPacketCount = it.txPacketCount + 2,
                lastTransmissionResult = resultSummary
            )
        }
    }

    private fun observeConnectionState() {
        connectionObserverJob?.cancel()
        connectionObserverJob = scope.launch {
            connectionManager.connectionState.collect { connState ->
                if (_telemetry.value.operationMode == OperationMode.REAL_VEHICLE) {
                    when (connState) {
                        ConnectionState.READY -> {
                            if (_telemetry.value.bridgeState == BridgeState.IDLE || _telemetry.value.bridgeState == BridgeState.CONNECTING) {
                                _telemetry.update { it.copy(bridgeState = BridgeState.READY) }
                            }
                        }
                        ConnectionState.CONNECTING -> {
                            _telemetry.update { it.copy(bridgeState = BridgeState.CONNECTING) }
                        }
                        ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                            if (_telemetry.value.isBridgeRunning) {
                                AppLogger.bridge("Connection dropped during active navigation. Failing safely.")
                                _telemetry.update { it.copy(bridgeState = BridgeState.ERROR) }
                            } else {
                                _telemetry.update { it.copy(bridgeState = BridgeState.IDLE) }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
