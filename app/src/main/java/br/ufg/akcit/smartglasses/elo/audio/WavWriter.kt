/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavWriter(private val sampleRate: Int = 16000) {
  companion object {
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16
    private const val HEADER_SIZE = 44
  }

  private val pcm = ByteArrayOutputStream()

  fun append(frame: ShortArray) {
    for (sample in frame) {
      val value = sample.toInt()
      pcm.write(value and 0xFF) // low byte first — little-endian, as WAV/PCM requires
      pcm.write((value shr 8) and 0xFF)
    }
  }

  fun appendBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
    pcm.write(bytes, offset, length)
  }

  /** Samples accumulated so far. */
  val sampleCount: Int
    get() = pcm.size() / 2

  fun toWavBytes(): ByteArray {
    val data = pcm.toByteArray()
    return buildHeader(data.size) + data
  }

  fun writeToFile(file: File) {
    FileOutputStream(file).use { it.write(toWavBytes()) }
  }

  fun reset() {
    pcm.reset()
  }

  private fun buildHeader(dataSize: Int): ByteArray {
    val byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
    val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
    val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
    buffer.putInt(36 + dataSize)
    buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
    buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
    buffer.putInt(16) // fmt chunk size (PCM)
    buffer.putShort(1) // audio format = PCM
    buffer.putShort(CHANNELS.toShort())
    buffer.putInt(sampleRate)
    buffer.putInt(byteRate)
    buffer.putShort(blockAlign.toShort())
    buffer.putShort(BITS_PER_SAMPLE.toShort())
    buffer.put("data".toByteArray(Charsets.US_ASCII))
    buffer.putInt(dataSize)
    return buffer.array()
  }
}
