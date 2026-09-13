package br.ufg.akcit.smartglasses.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class TextToSpeechManager(
    private val context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null,
) : TextToSpeech.OnInitListener {

    private val tag = "TextToSpeechManager"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var onSpeechDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ptBrLocale = Locale.forLanguageTag("pt-BR")
            val result = tts?.setLanguage(ptBrLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(tag, "Portuguese (BR) TTS language not supported, falling back to default locale")
                tts?.setLanguage(Locale.getDefault())
            }

            // Route audio via speech communication attributes so it plays clearly on glasses
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeechDoneCallback?.invoke()
                    onSpeechDoneCallback = null
                }

                @Suppress("OVERRIDE_DEPRECATION")
                @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeechDoneCallback = null
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(tag, "TTS Error ($errorCode) for utterance: $utteranceId")
                    _isSpeaking.value = false
                    onSpeechDoneCallback = null
                }
            })

            isInitialized = true
            Log.d(tag, "TextToSpeech initialized successfully")
            onInitComplete?.invoke(true)
        } else {
            Log.e(tag, "Failed to initialize TextToSpeech (status: $status)")
            isInitialized = false
            onInitComplete?.invoke(false)
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w(tag, "TextToSpeech not yet initialized")
            return
        }

        stop()
        onSpeechDoneCallback = onDone
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        onSpeechDoneCallback = null
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
