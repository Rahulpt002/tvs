package com.ntorqnav.bridge.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bridge_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LAST_DEVICE_MAC = stringPreferencesKey("last_device_mac")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val SIMULATION_MODE = booleanPreferencesKey("simulation_mode")
    }

    val lastDeviceMac: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_DEVICE_MAC] }
    val lastDeviceName: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_DEVICE_NAME] }
    val isAutoConnectEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_CONNECT] ?: false }
    val isSimulationMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.SIMULATION_MODE] ?: true }

    suspend fun saveLastDevice(mac: String, name: String?) {
        context.dataStore.edit {
            it[Keys.LAST_DEVICE_MAC] = mac
            if (name != null) it[Keys.LAST_DEVICE_NAME] = name
        }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    }

    suspend fun setSimulationMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SIMULATION_MODE] = enabled }
    }
}
