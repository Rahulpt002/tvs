package com.ntorqnav.bridge.navigation

import com.ntorqnav.bridge.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FakeNavigationProvider : NavigationProvider {
    override val name: String = "FakeNavigationProvider"

    private val _updates = MutableSharedFlow<NavigationUpdate>(replay = 1)
    private var isRunning = false
    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun start() {
        isRunning = true
        AppLogger.navigation("FakeNavigationProvider started")
    }

    override fun stop() {
        isRunning = false
        simulationJob?.cancel()
        simulationJob = null
        AppLogger.navigation("FakeNavigationProvider stopped")
    }

    override fun observeUpdates(): Flow<NavigationUpdate> = _updates.asSharedFlow()

    override fun isRunning(): Boolean = isRunning

    /**
     * Emit a manual navigation update (used by the Test Navigation UI screen).
     */
    fun sendManualUpdate(
        maneuver: Maneuver,
        distanceMeters: Int,
        roadName: String,
        etaMinutes: Int = 12,
        totalDistanceMeters: Int = 5000
    ) {
        val update = NavigationUpdate(
            maneuver = maneuver,
            distanceMeters = distanceMeters,
            roadName = roadName,
            etaMinutes = etaMinutes,
            timestamp = System.currentTimeMillis(),
            instruction = "${maneuver.name.replace('_', ' ')} on $roadName",
            totalDistanceMeters = totalDistanceMeters,
            source = "manual_test"
        )
        AppLogger.navigation("Manual test packet: ${update.toSummary()}")
        _updates.tryEmit(update)
    }

    /**
     * Start an automated simulation loop cycling through maneuvers.
     */
    fun startSimulation(intervalMs: Long = 3000L) {
        if (!isRunning) start()
        simulationJob?.cancel()
        simulationJob = scope.launch {
            val scenarios = listOf(
                Pair(Maneuver.STRAIGHT, 500 to "MG Road"),
                Pair(Maneuver.LEFT, 250 to "Indiranagar 100ft Road"),
                Pair(Maneuver.SLIGHT_RIGHT, 150 to "Outer Ring Road"),
                Pair(Maneuver.RIGHT, 80 to "Koramangala 80ft Road"),
                Pair(Maneuver.ROUNDABOUT, 40 to "Circle Roundabout"),
                Pair(Maneuver.DESTINATION, 10 to "Destination Arrived")
            )
            var index = 0
            var distRemaining = 4500
            var eta = 15
            while (isActive && isRunning) {
                val (maneuver, pair) = scenarios[index % scenarios.size]
                val (dist, road) = pair
                sendManualUpdate(
                    maneuver = maneuver,
                    distanceMeters = dist,
                    roadName = road,
                    etaMinutes = (eta - index).coerceAtLeast(1),
                    totalDistanceMeters = (distRemaining - (index * 600)).coerceAtLeast(100)
                )
                index++
                delay(intervalMs)
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }
}
