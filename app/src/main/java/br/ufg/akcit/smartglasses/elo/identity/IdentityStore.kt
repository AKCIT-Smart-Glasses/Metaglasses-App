/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.identity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Identity(val deviceId: String, val userId: String)

private val Context.eloIdentityDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "elo_identity")

class IdentityStore(context: Context) {
  private val dataStore = context.applicationContext.eloIdentityDataStore

  /** Null until both ids exist — a partially-written identity is treated as absent. */
  val identityFlow: Flow<Identity?> =
      dataStore.data.map { prefs ->
        val deviceId = prefs[KEY_DEVICE_ID]
        val userId = prefs[KEY_USER_ID]
        if (deviceId.isNullOrBlank() || userId.isNullOrBlank()) null
        else Identity(deviceId, userId)
      }

  suspend fun save(identity: Identity) {
    dataStore.edit { prefs ->
      prefs[KEY_DEVICE_ID] = identity.deviceId
      prefs[KEY_USER_ID] = identity.userId
    }
  }

  suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  private companion object {
    val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    val KEY_USER_ID = stringPreferencesKey("user_id")
  }
}
