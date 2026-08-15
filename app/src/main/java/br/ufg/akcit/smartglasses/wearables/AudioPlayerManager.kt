package br.ufg.akcit.smartglasses.wearables

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

class AudioPlayerManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val TAG = "AUDIO_PLAYER_MANAGER"

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }

    val currentPosition: Int
        get() = try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }

    val duration: Int
        get() = try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }

    fun play(filePath: String, onCompletion: () -> Unit) {
        release()
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.clearCommunicationDevice()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(filePath)

                val speakerDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speakerDevice != null) {
                    preferredDevice = speakerDevice
                    Log.d(TAG, "Routing playback to built-in speaker: ${speakerDevice.productName}")
                }

                prepare()

                try {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                        setTargetGain(1500) // 1500 mB = +15 dB boost
                        enabled = true
                    }
                    Log.d(TAG, "LoudnessEnhancer enabled with +15dB boost")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to initialize LoudnessEnhancer", e)
                }

                setVolume(1.0f, 1.0f)
                start()

                setOnCompletionListener {
                    onCompletion()
                    release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during playback", e)
            release()
            onCompletion()
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing audio", e)
        }
    }

    fun resume() {
        try {
            if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
                mediaPlayer?.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming audio", e)
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking audio", e)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio", e)
        }
        release()
    }

    fun release() {
        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing LoudnessEnhancer", e)
        }
        loudnessEnhancer = null

        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
    }
}
