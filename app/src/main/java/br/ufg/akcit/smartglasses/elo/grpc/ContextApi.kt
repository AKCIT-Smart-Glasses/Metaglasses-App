/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.grpc

import com.metaglass.proto.ContextGrpcKt
import com.metaglass.proto.StreamContextRequest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow

data class StreamContextResult(val contextId: String, val framesReceived: Int, val framesSelected: Int)

class ContextApi(private val channelProvider: GrpcChannelProvider) {
  companion object {
    private const val STREAM_DEADLINE_SECONDS = 900L
  }

  suspend fun streamContext(requests: Flow<StreamContextRequest>): StreamContextResult {
    val stub =
        ContextGrpcKt.ContextCoroutineStub(channelProvider.currentChannel())
            .withDeadlineAfter(STREAM_DEADLINE_SECONDS, TimeUnit.SECONDS)
    val response = stub.streamContext(requests)
    response.status.requireOk("StreamContext")
    return StreamContextResult(
        contextId = response.contextId,
        framesReceived = response.framesReceived,
        framesSelected = response.framesSelected,
    )
  }
}
