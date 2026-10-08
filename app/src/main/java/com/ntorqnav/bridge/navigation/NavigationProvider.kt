package com.ntorqnav.bridge.navigation

import kotlinx.coroutines.flow.Flow

/**
 * Provider interface for navigation sources.
 *
 * Implemented by:
 * - [GoogleMapsNotificationProvider] for live Google Maps notifications
 * - [FakeNavigationProvider] for interactive testing without GPS/Maps
 * - Replay navigation providers for simulating recorded sessions
 */
interface NavigationProvider {
    val name: String

    fun start()

    fun stop()

    fun observeUpdates(): Flow<NavigationUpdate>

    fun isRunning(): Boolean
}
