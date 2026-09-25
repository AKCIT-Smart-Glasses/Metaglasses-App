/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class TtsPlayer(context: Context) {
  companion object {
    private const val TAG = "Elo:TtsPlayer"
  }

  private val appContext = context.applicationContext
  private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private var player: MediaPlayer? = null
  private var file: File? = null
  private var focusRequest: AudioFocusRequest? = null

  var onPlaybackFinished: (() -> Unit)? = null

  /** Plays [audio] if non-empty. Calls [onCompletion] when audio finishes playing. */
  fun play(audio: ByteArray, mimeType: String, onCompletion: (() -> Unit)? = null): Boolean {
    if (audio.isEmpty()) return false
    reset()
    val newFile = writeTempFile(audio, mimeType)
    return try {
      val audioAttributes =
          AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()

      requestTransientAudioFocus(audioAttributes)
      ensureStreamVolume()

      val newPlayer =
          MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setVolume(1.0f, 1.0f)
            routeToPreferredDevice(this)
            setDataSource(newFile.absolutePath)
            setOnCompletionListener {
              Log.d(TAG, "TTS playback complete (${audio.size} bytes)")
              reset()
              onCompletion?.invoke()
              val finished = onPlaybackFinished
              onPlaybackFinished = null
              finished?.invoke()
            }
            setOnErrorListener { _, what, extra ->
              Log.w(TAG, "MediaPlayer error (what=$what, extra=$extra)")
              reset()
              onCompletion?.invoke()
              val finished = onPlaybackFinished
              onPlaybackFinished = null
              finished?.invoke()
              true
            }
            prepare()
            start()
          }
      player = newPlayer
      file = newFile
      Log.i(TAG, "TTS playback started: ${audio.size} bytes, mimeType=$mimeType, duration=${newPlayer.duration}ms")
      true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to play TTS audio", e)
      reset()
      false
    }
  }

  fun isPlaying(): Boolean = runCatching { player?.isPlaying == true }.getOrDefault(false)

  fun stop() {
    onPlaybackFinished = null
    reset()
  }

  private fun reset() {
    abandonTransientAudioFocus()
    player?.let { p ->
      runCatching { if (isPlaying()) p.stop() }
      runCatching { p.release() }
    }
    player = null
    file?.delete()
    file = null
  }

  private fun routeToPreferredDevice(mediaPlayer: MediaPlayer) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

    val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val btDevices = outputDevices.filter { dev ->
      dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
      dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

    // Prioritize Meta / Ray-Ban smartglasses if connected
    val preferredBt = btDevices.firstOrNull { dev ->
      val name = dev.productName.toString().lowercase()
      name.contains("ray-ban") || name.contains("meta") || name.contains("rb ") || name.contains("stories")
    } ?: btDevices.firstOrNull()

    if (preferredBt != null) {
      val routed = mediaPlayer.setPreferredDevice(preferredBt)
      Log.i(TAG, "Routing TTS audio to Bluetooth device: ${preferredBt.productName} (type=${preferredBt.type}, success=$routed)")
    } else {
      Log.i(TAG, "No Bluetooth A2DP/BLE device found, using system default audio route")
    }
  }

  private fun ensureStreamVolume() {
    try {
      val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
      val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
      Log.d(TAG, "STREAM_MUSIC volume: $currentVol / $maxVol")
      if (currentVol == 0) {
        val targetVol = (maxVol * 0.6f).toInt().coerceAtLeast(1)
        Log.w(TAG, "STREAM_MUSIC was muted (volume 0); raising to $targetVol for TTS audibility")
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Could not verify/adjust stream volume", e)
    }
  }

  private fun requestTransientAudioFocus(audioAttributes: AudioAttributes) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val req =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                  Log.d(TAG, "Audio focus changed: $focusChange")
                }
                .build()
        focusRequest = req
        audioManager.requestAudioFocus(req)
      } else {
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
        )
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to request audio focus", e)
    }
  }

  private fun abandonTransientAudioFocus() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
      } else {
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(null)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to abandon audio focus", e)
    }
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
