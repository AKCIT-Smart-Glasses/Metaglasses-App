/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.audio

private enum class EndpointState {
  IDLE,
  SPEAKING,
}

class SpeechEndpointer(
    private val vad: Vad,
    private val attackFrames: Int = DEFAULT_ATTACK_FRAMES,
    private val hangoverMs: Int = DEFAULT_HANGOVER_MS,
    private val sampleRate: Int = 16000,
) {
  companion object {
    const val DEFAULT_ATTACK_FRAMES = 3
    const val DEFAULT_HANGOVER_MS = 1200
  }

  private val frameDurationMs = (vad.hopSize * 1000L) / sampleRate

  private var state = EndpointState.IDLE
  private var consecutiveSpeechFrames = 0
  private var silenceMsSinceLastSpeech = 0L

  val isSpeaking: Boolean
    get() = state == EndpointState.SPEAKING

  var lastProbability: Float = 0f
    private set

  var lastFrameWasSpeech: Boolean = false
    private set

  fun offer(frame: ShortArray): Boolean {
    val result = vad.process(frame)
    lastProbability = result.probability
    lastFrameWasSpeech = result.isSpeech
    return when (state) {
      EndpointState.IDLE -> {
        consecutiveSpeechFrames = if (result.isSpeech) consecutiveSpeechFrames + 1 else 0
        if (consecutiveSpeechFrames >= attackFrames) {
          state = EndpointState.SPEAKING
          silenceMsSinceLastSpeech = 0
        }
        false
      }
      EndpointState.SPEAKING -> {
        if (result.isSpeech) {
          silenceMsSinceLastSpeech = 0
          false
        } else {
          silenceMsSinceLastSpeech += frameDurationMs
          if (silenceMsSinceLastSpeech >= hangoverMs) {
            reset()
            true
          } else {
            false
          }
        }
      }
    }
  }

  fun reset() {
    state = EndpointState.IDLE
    consecutiveSpeechFrames = 0
    silenceMsSinceLastSpeech = 0
  }
}
