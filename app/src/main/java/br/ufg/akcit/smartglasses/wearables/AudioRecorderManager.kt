package br.ufg.akcit.smartglasses.wearables

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import java.io.File

class AudioRecorderManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    private val TAG = "AUDIO_RECORDER_MANAGER"

    val isRecording: Boolean
        get() = mediaRecorder != null

    fun startRecording(): File {
        val fileName = "audio_${System.currentTimeMillis()}.m4a"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS), fileName)
        outputFile = file

        val selectedDevice: AudioDeviceInfo? = getBluetoothAudioDeviceInfo()

        if (selectedDevice != null) {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.setCommunicationDevice(selectedDevice)
        }

        mediaRecorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            setAudioChannels(1)
            setAudioSamplingRate(8000)
            setAudioEncodingBitRate(16000)

            setOutputFile(file.absolutePath)

            prepare()
            start()

            Log.d(TAG, "Started audio recording")
        }

        return file
    }

    fun stopRecording(): File? {
        mediaRecorder?.apply {
            stop()
            release()
            Log.d(TAG, "Stopped audio recording")
        }
        mediaRecorder = null
        return outputFile
    }

    fun release() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    fun getBluetoothAudioDeviceInfo(): AudioDeviceInfo? {
        val devices = audioManager.availableCommunicationDevices
        val userSelectedDeviceType = AudioDeviceInfo.TYPE_BLUETOOTH_SCO

        for (device in devices) {
            if (device.type == userSelectedDeviceType) {
                return device
            }
        }
        return null
    }
}
