package com.tz.cameratransfer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

object SettingsManager {
    private val IP_KEY = stringPreferencesKey("server_ip")
    private val PORT_KEY = stringPreferencesKey("server_port")

    suspend fun saveSettings(context: Context, ip: String, port: String) {
        context.dataStore.edit { settings ->
            settings[IP_KEY] = ip
            settings[PORT_KEY] = port
        }
    }

    fun getSettings(context: Context): Flow<Pair<String, String>> = context.dataStore.data.map { preferences ->
        val ip = preferences[IP_KEY] ?: "192.168.1.172"
        val port = preferences[PORT_KEY] ?: "8080"
        Pair(ip, port)
    }
}