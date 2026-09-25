/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.audio

import android.util.Log
import com.meta.wearable.dat.camera.types.AudioFrame
import java.nio.ByteOrder
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bridges raw PCM audio frames received from the Ray-Ban Meta glasses' camera DAT stream
 * into the voice processing pipeline (Vosk and WAV recording).
 *
 * Audio arrives from the glasses at 16 kHz mono 16-bit PCM little-endian.
 */
class GlassesAudioBridge {
  private val tag = "GlassesAudioBridge"

  // Buffered channel with DROP_OLDEST to ensure we never stall the camera stream collector
  private val channel =
      Channel<ByteArray>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val _isStreaming = MutableStateFlow(false)
  val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

  @Volatile private var firstFrameLogged = false

  fun setStreaming(streaming: Boolean) {
    _isStreaming.value = streaming
    if (!streaming) {
      firstFrameLogged = false
      // Drain channel on stop so stale audio is cleared
      while (channel.tryReceive().isSuccess) {}
      Log.d(tag, "Glasses audio streaming stopped and buffer drained")
    } else {
      Log.d(tag, "Glasses audio streaming started")
    }
  }

  fun onAudioFrame(frame: AudioFrame) {
    val buffer = frame.buffer
    if (!firstFrameLogged) {
      firstFrameLogged = true
      Log.i(
          tag,
          "First glasses audio frame received: ${buffer.remaining()} bytes, " +
              "pos=${buffer.position()}, limit=${buffer.limit()}, order=${buffer.order()}",
      )
    }

    val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    if (!duplicate.hasRemaining()) return
    val bytes = ByteArray(duplicate.remaining())
    duplicate.get(bytes)
    channel.trySend(bytes)
  }

  suspend fun receive(timeoutMs: Long = 400L): ByteArray? {
    return withTimeoutOrNull(timeoutMs) {
      channel.receive()
    }
  }

  fun tryReceive(): ByteArray? {
    return channel.tryReceive().getOrNull()
  }
}
