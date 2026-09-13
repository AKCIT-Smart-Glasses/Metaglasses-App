/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.session

import android.util.Log
import br.ufg.akcit.smartglasses.elo.grpc.EloApiException
import br.ufg.akcit.smartglasses.elo.grpc.SessionApi
import br.ufg.akcit.smartglasses.elo.identity.Identity
import br.ufg.akcit.smartglasses.elo.identity.IdentityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ConnectionState {
  data object Disconnected : ConnectionState

  data object Connecting : ConnectionState

  data class Connected(val sessionId: String, val userId: String) : ConnectionState

  data class Failed(val message: String) : ConnectionState
}

class EloConnectionManager(
    private val sessionApi: SessionApi,
    private val identityStore: IdentityStore,
    private val scope: CoroutineScope,
) {
  companion object {
    private const val TAG = "Elo:ConnectionManager"
    private const val HEARTBEAT_INTERVAL_MS = 25_000L
    private const val DEVICE_NAME = "SmartGlasses"
    private const val DEVICE_MODEL = "android-phone"
  }

  private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
  val state: StateFlow<ConnectionState> = _state.asStateFlow()

  private var heartbeatJob: Job? = null
  private var activeSessionId: String? = null

  suspend fun connect() {
    if (_state.value is ConnectionState.Connecting || _state.value is ConnectionState.Connected) {
      return
    }
    _state.value = ConnectionState.Connecting
    try {
      val identity = ensureIdentity()
      val sessionId = createSessionWithSelfHeal(identity)
      activeSessionId = sessionId
      _state.value = ConnectionState.Connected(sessionId, identity.userId)
      startHeartbeat(sessionId)
    } catch (e: Exception) {
      Log.e(TAG, "connect() failed", e)
      _state.value = ConnectionState.Failed(e.message ?: "erro desconhecido")
    }
  }

  suspend fun disconnect() {
    heartbeatJob?.cancel()
    heartbeatJob = null
    val sessionId = activeSessionId
    activeSessionId = null
    _state.value = ConnectionState.Disconnected
    if (sessionId != null) {
      runCatching { sessionApi.endSession(sessionId) }
          .onFailure { Log.w(TAG, "EndSession failed (disconnecting anyway)", it) }
    }
  }

  private suspend fun ensureIdentity(): Identity {
    identityStore.identityFlow.first()?.let { return it }
    val deviceId = sessionApi.registerDevice(DEVICE_NAME, DEVICE_MODEL)
    val userId = sessionApi.createUser(deviceId)
    val identity = Identity(deviceId, userId)
    identityStore.save(identity)
    return identity
  }

  private suspend fun createSessionWithSelfHeal(identity: Identity): String =
      try {
        sessionApi.createSession(identity.userId)
      } catch (e: EloApiException) {
        if (!e.looksLikeStaleIdentity()) throw e
        Log.w(TAG, "CreateSession failed for a stale identity — re-registering", e)
        identityStore.clear()
        val fresh = ensureIdentity()
        sessionApi.createSession(fresh.userId)
      }

  private fun startHeartbeat(sessionId: String) {
    heartbeatJob?.cancel()
    heartbeatJob =
        scope.launch {
          while (isActive) {
            delay(HEARTBEAT_INTERVAL_MS)
            try {
              sessionApi.heartbeat(sessionId)
            } catch (e: Exception) {
              Log.w(TAG, "Heartbeat failed", e)
            }
          }
        }
  }
}

private fun EloApiException.looksLikeStaleIdentity(): Boolean =
    serverMessage.contains("not found", ignoreCase = true)
