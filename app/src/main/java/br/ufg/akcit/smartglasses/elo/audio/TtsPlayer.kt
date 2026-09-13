/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.io.IOException

class TtsPlayer(context: Context) {
  companion object {
    private const val TAG = "Elo:TtsPlayer"
  }

  private val appContext = context.applicationContext
  private var player: MediaPlayer? = null
  private var file: File? = null

  /** Plays [audio] if non-empty. Calls [onCompletion] when audio finishes playing. */
  fun play(audio: ByteArray, mimeType: String, onCompletion: (() -> Unit)? = null): Boolean {
    if (audio.isEmpty()) return false
    reset()
    val newFile = writeTempFile(audio, mimeType)
    return try {
      val newPlayer =
          MediaPlayer().apply {
            setDataSource(newFile.absolutePath)
            setOnCompletionListener {
              reset()
              onCompletion?.invoke()
            }
            setOnErrorListener { _, what, extra ->
              Log.w(TAG, "MediaPlayer error (what=$what, extra=$extra)")
              reset()
              onCompletion?.invoke()
              true
            }
            prepare()
            start()
          }
      player = newPlayer
      file = newFile
      true
    } catch (e: IOException) {
      Log.w(TAG, "Failed to play TTS audio", e)
      newFile.delete()
      false
    }
  }

  fun isPlaying(): Boolean = player?.isPlaying == true

  fun stop() = reset()

  private fun reset() {
    player?.let { p ->
      runCatching { if (p.isPlaying) p.stop() }
      p.release()
    }
    player = null
    file?.delete()
    file = null
  }

  private fun writeTempFile(audio: ByteArray, mimeType: String): File {
    val dir = File(appContext.cacheDir, "elo_tts").apply { mkdirs() }
    val file = File(dir, "tts_${System.currentTimeMillis()}.${extensionFor(mimeType)}")
    file.writeBytes(audio)
    return file
  }

  private fun extensionFor(mimeType: String): String =
      if (mimeType.contains("wav", ignoreCase = true)) "wav" else "mp3"
}
