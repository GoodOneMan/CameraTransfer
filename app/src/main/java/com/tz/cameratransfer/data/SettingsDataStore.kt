package com.tz.cameratransfer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Хранилище настроек подключения к Windows-серверу.
 * Использует Preferences DataStore — типобезопасную замену SharedPreferences.
 */
class SettingsDataStore(context: Context) {

    companion object {
        val SERVER_IP = stringPreferencesKey("server_ip")
        val SERVER_PORT = intPreferencesKey("server_port")
        const val DEFAULT_IP = "192.168.1.100"
        const val DEFAULT_PORT = 5000
    }

    private val dataStore = context.dataStore

    val serverIp: Flow<String> = dataStore.data.map { prefs ->
        prefs[SERVER_IP] ?: DEFAULT_IP
    }

    val serverPort: Flow<Int> = dataStore.data.map { prefs ->
        prefs[SERVER_PORT] ?: DEFAULT_PORT
    }

    suspend fun saveSettings(ip: String, port: Int) {
        dataStore.edit { prefs ->
            prefs[SERVER_IP] = ip
            prefs[SERVER_PORT] = port
        }
    }
}