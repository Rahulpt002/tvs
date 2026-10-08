package com.ntorqnav.bridge.navigation

import com.ntorqnav.bridge.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class GoogleMapsNotificationProvider : NavigationProvider {
    override val name: String = "GoogleMapsNotificationProvider"

    private val _updates = MutableSharedFlow<NavigationUpdate>(replay = 1)
    private var job: Job? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun start() {
        if (isRunning) return
        isRunning = true
        AppLogger.maps("Starting GoogleMapsNotificationProvider")

        job = scope.launch {
            GoogleMapsNotificationListenerService.rawNotificationFlow.collect { payload ->
                val parsed = GoogleMapsNotificationParser.parse(
                    title = payload.title,
                    text = payload.text,
                    subText = payload.subText,
                    timestamp = payload.postTime
                )
                if (parsed != null) {
                    AppLogger.navigation("Parsed NavUpdate: ${parsed.toSummary()}")
                    _updates.emit(parsed)
                }
            }
        }
    }

    override fun stop() {
        isRunning = false
        job?.cancel()
        job = null
        AppLogger.maps("Stopped GoogleMapsNotificationProvider")
    }

    override fun observeUpdates(): Flow<NavigationUpdate> = _updates.asSharedFlow()

    override fun isRunning(): Boolean = isRunning

    fun isListenerServiceConnected(): Boolean = GoogleMapsNotificationListenerService.isConnected
}
