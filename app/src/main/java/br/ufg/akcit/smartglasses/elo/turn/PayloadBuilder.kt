/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.turn

import android.graphics.Bitmap
import com.google.protobuf.ByteString
import com.metaglass.proto.MediaPayload
import com.metaglass.proto.mediaPayload
import java.io.ByteArrayOutputStream

object PayloadBuilder {
  private const val JPEG_QUALITY = 85
  private const val CONTEXT_HINT_KEY = "context_hint"
  private const val CONTEXT_HINT_VALUE = "user_query"

  fun buildQueryPayloads(wavBytes: ByteArray, sampleRate: Int, photo: Bitmap?): List<MediaPayload> {
    val payloads = mutableListOf(audioPayload(wavBytes, sampleRate))
    photo?.let { payloads += imagePayload(it) }
    return payloads
  }

  private fun audioPayload(wavBytes: ByteArray, sampleRate: Int): MediaPayload = mediaPayload {
    data = ByteString.copyFrom(wavBytes)
    mimeType = "audio/wav"
    metadata[CONTEXT_HINT_KEY] = CONTEXT_HINT_VALUE
    metadata["sample_rate"] = sampleRate.toString()
  }

  private fun imagePayload(bitmap: Bitmap): MediaPayload {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
    return mediaPayload {
      data = ByteString.copyFrom(stream.toByteArray())
      mimeType = "image/jpeg"
      metadata[CONTEXT_HINT_KEY] = CONTEXT_HINT_VALUE
      metadata["width"] = bitmap.width.toString()
      metadata["height"] = bitmap.height.toString()
    }
  }
}
