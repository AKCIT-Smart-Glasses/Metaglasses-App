package br.ufg.akcit.smartglasses.wearables

import android.media.MediaPlayer

class AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null

    fun play(filePath: String, onCompletion: () -> Unit) {
        release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            start()
            setOnCompletionListener {
                onCompletion()
                release()
            }
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        release()
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true
}
