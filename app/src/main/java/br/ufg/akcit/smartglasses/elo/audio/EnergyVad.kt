/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.audio

import kotlin.math.sqrt

class EnergyVad(override val hopSize: Int = DEFAULT_HOP_SIZE) : Vad {
  companion object {
    const val DEFAULT_HOP_SIZE = 256
    private const val SPEECH_MARGIN_DB = 12.0
    private val SPEECH_RATIO = Math.pow(10.0, SPEECH_MARGIN_DB / 20.0)
    private const val FLOOR_ATTACK = 0.05
    private const val FLOOR_RELEASE = 0.01
    private const val INITIAL_FLOOR_RMS = 50.0
  }

  private var noiseFloorRms = INITIAL_FLOOR_RMS

  override fun process(frame: ShortArray): VadFrameResult {
    require(frame.size == hopSize) { "Expected $hopSize samples, got ${frame.size}" }
    var sumSquares = 0.0
    for (sample in frame) sumSquares += sample.toDouble() * sample.toDouble()
    val rms = sqrt(sumSquares / frame.size)

    val isSpeech = rms > noiseFloorRms * SPEECH_RATIO
    if (!isSpeech) {
      val rate = if (rms > noiseFloorRms) FLOOR_ATTACK else FLOOR_RELEASE
      noiseFloorRms += (rms - noiseFloorRms) * rate
    }
    return VadFrameResult(probability = if (isSpeech) 1f else 0f, isSpeech = isSpeech)
  }

  override fun close() = Unit
}
