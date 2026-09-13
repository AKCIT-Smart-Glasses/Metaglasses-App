/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.grpc

import com.metaglass.proto.SessionGrpcKt
import com.metaglass.proto.createSessionRequest
import com.metaglass.proto.createUserRequest
import com.metaglass.proto.endSessionRequest
import com.metaglass.proto.heartbeatRequest
import com.metaglass.proto.registerDeviceRequest

private const val CLIENT_METADATA_KEY = "client"
private const val CLIENT_METADATA_VALUE = "smartglasses-android"

class SessionApi(private val channelProvider: GrpcChannelProvider) {

  private suspend fun stub() = SessionGrpcKt.SessionCoroutineStub(channelProvider.currentChannel())

  /** Registers a new device row; returns the server-assigned device_id. */
  suspend fun registerDevice(deviceName: String, deviceModel: String): String {
    val response =
        stub()
            .registerDevice(
                registerDeviceRequest {
                  this.deviceName = deviceName
                  this.deviceModel = deviceModel
                  metadata[CLIENT_METADATA_KEY] = CLIENT_METADATA_VALUE
                }
            )
    response.status.requireOk("RegisterDevice")
    return response.deviceId
  }

  /** Creates a new user profile tied to [deviceId]; returns the server-assigned user_id. */
  suspend fun createUser(deviceId: String, preferredLanguage: String = "pt-BR"): String {
    val response =
        stub()
            .createUser(
                createUserRequest {
                  name = "SmartGlasses"
                  this.preferredLanguage = preferredLanguage
                  this.deviceId = deviceId
                  metadata[CLIENT_METADATA_KEY] = CLIENT_METADATA_VALUE
                }
            )
    response.status.requireOk("CreateUser")
    return response.userId
  }

  /** Starts a new session for [userId]; returns the server-assigned session_id. */
  suspend fun createSession(userId: String): String {
    val response =
        stub()
            .createSession(
                createSessionRequest {
                  this.userId = userId
                  initialMetadata[CLIENT_METADATA_KEY] = CLIENT_METADATA_VALUE
                  initialMetadata["language"] = "pt-BR"
                }
            )
    response.status.requireOk("CreateSession")
    return response.sessionId
  }

  /** Keeps [sessionId] alive. */
  suspend fun heartbeat(sessionId: String) {
    val response =
        stub()
            .heartbeat(
                heartbeatRequest {
                  this.sessionId = sessionId
                  timestampMs = System.currentTimeMillis()
                }
            )
    response.status.requireOk("Heartbeat")
  }

  suspend fun endSession(sessionId: String) {
    val response = stub().endSession(endSessionRequest { this.sessionId = sessionId })
    response.status.requireOk("EndSession")
  }
}
