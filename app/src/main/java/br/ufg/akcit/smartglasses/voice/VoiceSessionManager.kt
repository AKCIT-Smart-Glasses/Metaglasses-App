/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import br.ufg.akcit.smartglasses.SmartGlassesApp
import br.ufg.akcit.smartglasses.elo.EloContainer
import br.ufg.akcit.smartglasses.elo.audio.WavWriter
import br.ufg.akcit.smartglasses.elo.session.ConnectionState
import br.ufg.akcit.smartglasses.elo.turn.PayloadBuilder
import br.ufg.akcit.smartglasses.elo.turn.PhotoCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

enum class VoiceSessionState {
    IDLE,                  // Ready for user to tap and speak
    INITIALIZING,          // Preparing audio, Vosk model, and backend session
    LISTENING_COMMAND,     // Actively recording and transcribing user question
    LISTENING_WAKE_WORD,   // Optional hands-free mode listening for "Olá Óculos"
    PROCESSING,            // Sending question to gRPC orchestrator
    SPEAKING,              // Narrating response via ElevenLabs / TTS
    ERROR                  // An error occurred
}

data class VoiceSessionUiState(
    val state: VoiceSessionState = VoiceSessionState.IDLE,
    val isSessionActive: Boolean = false,
    val isHandsFreeWakeWordActive: Boolean = false,
    val lastRecognizedWakeWord: String? = null,
    val partialTranscription: String = "",
    val finalCommand: String? = null,
    val assistantResponse: String? = null,
    val errorMessage: String? = null,
    val isGlassesMicActive: Boolean = false,
    val amplitudeLevel: Float = 0f,
    val isConnectedToBackend: Boolean = false,
) {
    val geminiResponse: String? get() = assistantResponse
}

class VoiceSessionManager(
    private val context: Context,
    private val container: EloContainer = (context.applicationContext as SmartGlassesApp).container,
    private val soundFeedback: SoundFeedbackHelper = SoundFeedbackHelper(),
    private val modelManager: VoskModelManager = VoskModelManager(context),
    var photoCapture: PhotoCapture? = null,
) {
    private val tag = "VoiceSessionManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(VoiceSessionUiState())
    val uiState: StateFlow<VoiceSessionUiState> = _uiState.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var listeningJob: Job? = null
    private var notificationJob: Job? = null
    private var connectionJob: Job? = null
    private var loadedModel: Model? = null

    private val sampleRate = 16000f
    private val wakeWordGrammar = "[\"olá óculos\", \"ola oculos\", \"óculos\", \"oculos\", \"hey assistente\", \"ei assistente\", \"[unk]\"]"

    // VAD & Silence detection thresholds
    private val silenceThresholdAmplitude = 0.035f
    private val silenceDurationMs = 1500L
    private val initialSpeechTimeoutMs = 8000L

    @Volatile
    private var requestDirectCommand: Boolean = false
    @Volatile
    private var requestFinishEarly: Boolean = false
    @Volatile
    private var receivedAnswerNotification: Boolean = false

    init {
        // Monitor orchestrator connection state
        connectionJob = scope.launch {
            container.connectionManager.state.collect { connState ->
                _uiState.update { it.copy(isConnectedToBackend = connState is ConnectionState.Connected) }
            }
        }

        // Monitor server-side notifications (including holding phrases and early answers)
        notificationJob = scope.launch {
            container.notificationSubscriber.notifications.collect { notification ->
                handleServerNotification(notification)
            }
        }
    }

    private fun handleServerNotification(notification: com.metaglass.proto.Notification) {
        Log.i(tag, "Notification from backend: type=${notification.type}, text=\"${notification.text}\"")
        if (_uiState.value.state == VoiceSessionState.PROCESSING || _uiState.value.state == VoiceSessionState.SPEAKING) {
            if (notification.type == "holding") {
                if (notification.text.isNotBlank()) {
                    _uiState.update { it.copy(partialTranscription = notification.text) }
                }
            } else if (notification.type == "answer") {
                receivedAnswerNotification = true
                if (notification.text.isNotBlank()) {
                    _uiState.update {
                        it.copy(
                            state = VoiceSessionState.SPEAKING,
                            assistantResponse = notification.text,
                        )
                    }
                }
            }
        }
    }

    /**
     * Primary entry point for Criterion 6: Pushing / tapping the button directly
     * triggers question collection.
     */
    fun onMainButtonClicked() {
        when (_uiState.value.state) {
            VoiceSessionState.IDLE, VoiceSessionState.LISTENING_WAKE_WORD -> {
                startDirectCommandListening()
            }
            VoiceSessionState.LISTENING_COMMAND -> {
                // If user taps while listening, finish recording early and process
                requestFinishEarly = true
            }
            VoiceSessionState.SPEAKING -> {
                // If user taps while speaking, interrupt response and return to IDLE
                container.ttsPlayer.stop()
                returnToIdleOrWakeWord()
            }
            VoiceSessionState.PROCESSING -> {
                Log.d(tag, "Main button tapped while processing")
            }
            VoiceSessionState.INITIALIZING -> {
                Log.d(tag, "Main button tapped while initializing")
            }
            VoiceSessionState.ERROR -> {
                _uiState.update { it.copy(state = VoiceSessionState.IDLE, errorMessage = null) }
                startDirectCommandListening()
            }
        }
    }

    /**
     * Starts listening directly for the user's question without requiring a wake word.
     */
    fun startDirectCommandListening() {
        requestDirectCommand = true
        soundFeedback.playWakeBeep()

        if (!_uiState.value.isSessionActive) {
            startSessionInternal(startInCommandMode = true)
        } else {
            _uiState.update {
                it.copy(
                    state = VoiceSessionState.LISTENING_COMMAND,
                    partialTranscription = "",
                    finalCommand = null,
                    assistantResponse = null,
                    errorMessage = null,
                )
            }
        }
    }

    /**
     * Toggles optional hands-free wake word mode ("Olá Óculos").
     */
    fun toggleHandsFreeWakeWord() {
        val newHandsFree = !_uiState.value.isHandsFreeWakeWordActive
        _uiState.update { it.copy(isHandsFreeWakeWordActive = newHandsFree) }

        if (newHandsFree) {
            if (!_uiState.value.isSessionActive) {
                startSessionInternal(startInCommandMode = false)
            } else if (_uiState.value.state == VoiceSessionState.IDLE) {
                _uiState.update { it.copy(state = VoiceSessionState.LISTENING_WAKE_WORD) }
            }
        } else {
            if (_uiState.value.state == VoiceSessionState.LISTENING_WAKE_WORD) {
                _uiState.update { it.copy(state = VoiceSessionState.IDLE) }
            }
        }
    }

    fun startSession() {
        startDirectCommandListening()
    }

    private fun startSessionInternal(startInCommandMode: Boolean) {
        _uiState.update {
            it.copy(
                state = if (startInCommandMode) VoiceSessionState.LISTENING_COMMAND else VoiceSessionState.INITIALIZING,
                isSessionActive = true,
                errorMessage = null,
                finalCommand = null,
                assistantResponse = null,
                partialTranscription = "",
            )
        }

        listeningJob?.cancel()
        listeningJob = scope.launch {
            try {
                // Ensure backend orchestrator is connected
                launch {
                    container.connectionManager.connect()
                }

                setupBluetoothAudioRouting()

                if (loadedModel == null) {
                    _uiState.update { it.copy(state = VoiceSessionState.INITIALIZING) }
                    loadedModel = modelManager.getOrInitModel()
                }

                runAudioListeningLoop(loadedModel!!, initialCommandMode = startInCommandMode)
            } catch (e: Exception) {
                Log.e(tag, "Failed to start voice session", e)
                soundFeedback.playErrorTone()
                _uiState.update {
                    it.copy(
                        state = VoiceSessionState.ERROR,
                        isSessionActive = false,
                        errorMessage = e.localizedMessage ?: "Erro ao iniciar sessão de voz",
                    )
                }
                cleanupAudio()
            }
        }
    }

    fun stopSession() {
        listeningJob?.cancel()
        listeningJob = null
        container.ttsPlayer.stop()
        cleanupAudio()
        _uiState.update {
            it.copy(
                state = VoiceSessionState.IDLE,
                isSessionActive = false,
                partialTranscription = "",
                amplitudeLevel = 0f,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runAudioListeningLoop(model: Model, initialCommandMode: Boolean) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate.toInt(),
            channelConfig,
            audioEncoding,
        )
        val bufferSize = maxOf(minBufferSize, 4096)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate.toInt(),
            channelConfig,
            audioEncoding,
            bufferSize,
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord initialization failed")
        }

        audioRecord?.startRecording()
        Log.d(tag, "AudioRecord started. initialCommandMode=$initialCommandMode")

        val buffer = ByteArray(2048)
        val wavWriter = WavWriter(sampleRate.toInt())

        var inCommandMode = initialCommandMode
        var currentRecognizer = if (inCommandMode) {
            Recognizer(model, sampleRate)
        } else {
            Recognizer(model, sampleRate, wakeWordGrammar)
        }

        _uiState.update {
            it.copy(
                state = if (inCommandMode) VoiceSessionState.LISTENING_COMMAND
                else if (it.isHandsFreeWakeWordActive) VoiceSessionState.LISTENING_WAKE_WORD
                else VoiceSessionState.IDLE
            )
        }

        var commandStartTime = if (inCommandMode) System.currentTimeMillis() else 0L
        var hasSpoken = false
        var lastSpeechTimestamp = 0L

        while (scope.isActive && _uiState.value.isSessionActive) {
            // Check for direct command request triggered from UI button
            if (requestDirectCommand && !inCommandMode) {
                requestDirectCommand = false
                inCommandMode = true
                hasSpoken = false
                lastSpeechTimestamp = 0L
                commandStartTime = System.currentTimeMillis()
                wavWriter.reset()
                currentRecognizer.close()
                currentRecognizer = Recognizer(model, sampleRate)
                _uiState.update {
                    it.copy(
                        state = VoiceSessionState.LISTENING_COMMAND,
                        partialTranscription = "",
                    )
                }
            }

            val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
            if (readBytes > 0) {
                val amplitude = calculateRmsAmplitude(buffer, readBytes)
                _uiState.update { it.copy(amplitudeLevel = amplitude) }

                val currentState = _uiState.value.state
                if (currentState == VoiceSessionState.PROCESSING || currentState == VoiceSessionState.SPEAKING) {
                    continue
                }

                if (!inCommandMode) {
                    // STAGE 1: Listening for Wake Word (Hands-free mode only)
                    if (!_uiState.value.isHandsFreeWakeWordActive) {
                        continue
                    }

                    if (currentRecognizer.acceptWaveForm(buffer, readBytes)) {
                        val resultText = extractTextFromJson(currentRecognizer.result)
                        if (isWakeWordMatch(resultText)) {
                            Log.d(tag, "Wake word detected: $resultText")
                            onWakeWordTriggered()

                            inCommandMode = true
                            hasSpoken = false
                            lastSpeechTimestamp = 0L
                            commandStartTime = System.currentTimeMillis()
                            wavWriter.reset()
                            currentRecognizer.close()
                            currentRecognizer = Recognizer(model, sampleRate)
                            _uiState.update {
                                it.copy(
                                    state = VoiceSessionState.LISTENING_COMMAND,
                                    lastRecognizedWakeWord = resultText,
                                    partialTranscription = "",
                                )
                            }
                        }
                    } else {
                        val partial = extractPartialFromJson(currentRecognizer.partialResult)
                        if (isWakeWordMatch(partial)) {
                            Log.d(tag, "Wake word detected (partial): $partial")
                            onWakeWordTriggered()

                            inCommandMode = true
                            hasSpoken = false
                            lastSpeechTimestamp = 0L
                            commandStartTime = System.currentTimeMillis()
                            wavWriter.reset()
                            currentRecognizer.close()
                            currentRecognizer = Recognizer(model, sampleRate)
                            _uiState.update {
                                it.copy(
                                    state = VoiceSessionState.LISTENING_COMMAND,
                                    lastRecognizedWakeWord = partial,
                                    partialTranscription = "",
                                )
                            }
                        }
                    }
                } else {
                    // STAGE 2: Listening for Command (Tap-to-Talk or after Wake Word)
                    // Accumulate raw PCM frames into the WAV writer
                    wavWriter.appendBytes(buffer, 0, readBytes)

                    val now = System.currentTimeMillis()
                    val isVoiceDetected = amplitude >= silenceThresholdAmplitude

                    val isFinal = currentRecognizer.acceptWaveForm(buffer, readBytes)
                    val partial = extractPartialFromJson(currentRecognizer.partialResult)
                    if (partial.isNotBlank() && partial != _uiState.value.partialTranscription) {
                        _uiState.update { it.copy(partialTranscription = partial) }
                        hasSpoken = true
                        lastSpeechTimestamp = now
                    }

                    if (isVoiceDetected) {
                        hasSpoken = true
                        lastSpeechTimestamp = now
                    }

                    // Check silence threshold after user has spoken
                    val silenceDuration = if (hasSpoken) now - lastSpeechTimestamp else 0L
                    val silenceDetected = hasSpoken && (silenceDuration >= silenceDurationMs)
                    val finishEarly = requestFinishEarly
                    requestFinishEarly = false

                    if (silenceDetected || isFinal || finishEarly) {
                        val resultText = extractTextFromJson(currentRecognizer.result).trim()
                        val finalCommandText = if (resultText.isNotBlank()) resultText else _uiState.value.partialTranscription.trim()

                        if (finalCommandText.isNotBlank() || wavWriter.sampleCount > 0) {
                            Log.d(tag, "Voice question captured: \"$finalCommandText\" (silence: ${silenceDuration}ms, early: $finishEarly)")
                            soundFeedback.playConfirmTone()
                            _uiState.update {
                                it.copy(
                                    state = VoiceSessionState.PROCESSING,
                                    finalCommand = finalCommandText.ifBlank { "..." },
                                    partialTranscription = finalCommandText,
                                )
                            }

                            val wavBytes = wavWriter.toWavBytes()
                            wavWriter.reset()

                            scope.launch {
                                processCommandWithOrchestrator(wavBytes, finalCommandText) {
                                    inCommandMode = false
                                    hasSpoken = false
                                    currentRecognizer.close()
                                    currentRecognizer = Recognizer(model, sampleRate, wakeWordGrammar)
                                    returnToIdleOrWakeWord()
                                }
                            }
                        } else if (finishEarly) {
                            soundFeedback.playErrorTone()
                            wavWriter.reset()
                            inCommandMode = false
                            hasSpoken = false
                            currentRecognizer.close()
                            currentRecognizer = Recognizer(model, sampleRate, wakeWordGrammar)
                            returnToIdleOrWakeWord()
                        }
                    } else if (!hasSpoken && (now - commandStartTime >= initialSpeechTimeoutMs)) {
                        Log.d(tag, "Command listening timeout: no speech detected")
                        soundFeedback.playErrorTone()
                        wavWriter.reset()
                        inCommandMode = false
                        hasSpoken = false
                        currentRecognizer.close()
                        currentRecognizer = Recognizer(model, sampleRate, wakeWordGrammar)
                        returnToIdleOrWakeWord()
                    }
                }
            }
        }

        currentRecognizer.close()
    }

    private fun returnToIdleOrWakeWord() {
        _uiState.update {
            it.copy(
                state = if (it.isHandsFreeWakeWordActive) VoiceSessionState.LISTENING_WAKE_WORD else VoiceSessionState.IDLE,
                partialTranscription = "",
                amplitudeLevel = 0f,
            )
        }
    }

    private fun onWakeWordTriggered() {
        soundFeedback.playWakeBeep()
    }

    private suspend fun processCommandWithOrchestrator(
        wavBytes: ByteArray,
        commandTextHint: String,
        onComplete: () -> Unit,
    ) {
        try {
            receivedAnswerNotification = false

            // 1. Ensure backend session is connected
            var connState = container.connectionManager.state.value
            if (connState !is ConnectionState.Connected) {
                container.connectionManager.connect()
                connState = container.connectionManager.state.first {
                    it is ConnectionState.Connected || it is ConnectionState.Failed
                }
            }

            val sessionId = (connState as? ConnectionState.Connected)?.sessionId
            if (sessionId == null) {
                val errMsg = "Não conectado ao servidor do assistente"
                Log.e(tag, errMsg)
                handleProcessingError(errMsg, onComplete)
                return
            }

            // Ensure notification stream is subscribed for holding audio
            container.notificationSubscriber.start(sessionId)

            // 2. Capture snapshot from glasses camera if active
            val photo = photoCapture?.capturePhoto()
            Log.d(tag, "Captured photo: ${if (photo != null) "${photo.width}x${photo.height}" else "none"}")

            // 3. Assemble MediaPayload (audio/wav + optional image/jpeg)
            val payloads = PayloadBuilder.buildQueryPayloads(wavBytes, sampleRate.toInt(), photo)
            Log.d(tag, "Calling Agent.Interact with ${payloads.size} payload(s), sessionId=$sessionId")

            // 4. Call Agent.Interact RPC
            val result = container.agentApi.interact(sessionId, payloads)
            Log.i(tag, "Interact response received: query=\"${result.query}\" response=\"${result.response}\" audioBytes=${result.audio.size}")

            val resolvedQuery = result.query.ifBlank { commandTextHint }
            val reply = result.response

            _uiState.update {
                it.copy(
                    state = VoiceSessionState.SPEAKING,
                    finalCommand = resolvedQuery,
                    assistantResponse = reply,
                )
            }

            // 5. Play synthesized audio response (ElevenLabs MP3 returned by orchestrator)
            val audioBytes = result.audio
            if (audioBytes.isNotEmpty()) {
                val played = container.ttsPlayer.play(audioBytes, result.audioMimeType) {
                    onComplete()
                }
                if (!played) {
                    onComplete()
                }
            } else {
                // If audio was already delivered via early Notification (e.g. SPEAK_EARLY / direct triage),
                // wait for TtsPlayer to finish playing the notification audio.
                if (container.ttsPlayer.isPlaying()) {
                    container.ttsPlayer.onPlaybackFinished = {
                        onComplete()
                    }
                } else if (receivedAnswerNotification) {
                    container.ttsPlayer.onPlaybackFinished = {
                        onComplete()
                    }
                    scope.launch {
                        delay(1500L)
                        if (!container.ttsPlayer.isPlaying()) {
                            onComplete()
                        }
                    }
                } else {
                    // Text-only mode: display response on screen for 3s then complete
                    scope.launch {
                        delay(3000L)
                        onComplete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to process question with orchestrator", e)
            handleProcessingError(
                "Desculpe, tive uma instabilidade ao me conectar com o assistente. Pode tentar novamente?",
                onComplete,
            )
        }
    }

    private fun handleProcessingError(message: String, onComplete: () -> Unit) {
        soundFeedback.playErrorTone()
        _uiState.update {
            it.copy(
                state = VoiceSessionState.SPEAKING,
                assistantResponse = message,
                errorMessage = message,
            )
        }
        scope.launch {
            delay(3000L)
            onComplete()
        }
    }

    private fun isWakeWordMatch(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("olá óculos") ||
                lower.contains("ola oculos") ||
                lower.contains("óculos") ||
                lower.contains("oculos") ||
                lower.contains("hey assistente") ||
                lower.contains("ei assistente") ||
                lower.contains("assistente")
    }

    private fun extractTextFromJson(jsonString: String): String {
        return try {
            JSONObject(jsonString).optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractPartialFromJson(jsonString: String): String {
        return try {
            JSONObject(jsonString).optString("partial", "")
        } catch (e: Exception) {
            ""
        }
    }

    private fun calculateRmsAmplitude(buffer: ByteArray, readBytes: Int): Float {
        var sum = 0.0
        var samples = 0
        var i = 0
        while (i < readBytes - 1) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += sample * sample
            samples++
            i += 2
        }
        if (samples == 0) return 0f
        val rms = Math.sqrt(sum / samples)
        return (rms / 32767.0).toFloat().coerceIn(0f, 1f)
    }

    private fun setupBluetoothAudioRouting() {
        val selectedDevice = getBluetoothAudioDeviceInfo()
        if (selectedDevice != null) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            val success = audioManager.setCommunicationDevice(selectedDevice)
            Log.d(tag, "Bluetooth audio communication device set: $success (${selectedDevice.productName})")
            _uiState.update { it.copy(isGlassesMicActive = true) }
        } else {
            Log.w(tag, "No Bluetooth SCO device found, using internal microphone")
            _uiState.update { it.copy(isGlassesMicActive = false) }
        }
    }

    private fun getBluetoothAudioDeviceInfo(): AudioDeviceInfo? {
        val devices = audioManager.availableCommunicationDevices
        for (device in devices) {
            if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            ) {
                return device
            }
        }
        return null
    }

    private fun cleanupAudio() {
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioRecord", e)
        }
        audioRecord = null
        audioManager.clearCommunicationDevice()
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    fun release() {
        stopSession()
        notificationJob?.cancel()
        connectionJob?.cancel()
        soundFeedback.release()
        modelManager.release()
        scope.cancel()
    }
}
