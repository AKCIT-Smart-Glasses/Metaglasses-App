package br.ufg.akcit.smartglasses.elo.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.ufg.akcit.smartglasses.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class OrchestratorTarget(val host: String, val port: Int)

private val Context.eloSettingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "elo_settings")

class EloSettingsStore(context: Context) {
  private val dataStore = context.applicationContext.eloSettingsDataStore

  val default: OrchestratorTarget =
      OrchestratorTarget(BuildConfig.ORCHESTRATOR_HOST, BuildConfig.ORCHESTRATOR_PORT)

  val targetFlow: Flow<OrchestratorTarget> =
      dataStore.data.map { prefs ->
        OrchestratorTarget(
            host = prefs[KEY_HOST] ?: default.host,
            port = prefs[KEY_PORT] ?: default.port,
        )
      }

  suspend fun setTarget(host: String, port: Int) {
    dataStore.edit { prefs ->
      prefs[KEY_HOST] = host
      prefs[KEY_PORT] = port
    }
  }

  suspend fun resetToDefault() {
    dataStore.edit { it.clear() }
  }

  private companion object {
    val KEY_HOST = stringPreferencesKey("orchestrator_host")
    val KEY_PORT = intPreferencesKey("orchestrator_port")
  }
}
