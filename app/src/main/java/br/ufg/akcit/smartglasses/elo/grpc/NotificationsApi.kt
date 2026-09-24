/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.grpc

import com.metaglass.proto.Notification
import com.metaglass.proto.NotificationsGrpcKt
import com.metaglass.proto.subscribeRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class NotificationsApi(private val channelProvider: GrpcChannelProvider) {

  /** A fresh, cold flow per call — the channel is resolved at collection time. */
  fun subscribe(sessionId: String): Flow<Notification> = flow {
    val stub = NotificationsGrpcKt.NotificationsCoroutineStub(channelProvider.currentChannel())
    emitAll(stub.subscribe(subscribeRequest { this.sessionId = sessionId }))
  }
}
