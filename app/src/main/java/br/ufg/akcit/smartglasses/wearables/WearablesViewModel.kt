/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// WearablesViewModel - Core DAT SDK Integration
//
// This ViewModel demonstrates the core DAT API patterns for:
// - Device registration and unregistration using the DAT SDK
// - Permission management for wearable devices
// - Device discovery and state management
// - Integration with MockDeviceKit for testing

package br.ufg.akcit.smartglasses.wearables

import android.app.Activity
import android.app.Application
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.ufg.akcit.smartglasses.R
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceIdentifier
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WearablesViewModel(private val application: Application) : AndroidViewModel(application) {
  private val _uiState = MutableStateFlow(WearablesUiState())
  val uiState: StateFlow<WearablesUiState> = _uiState.asStateFlow()

  // AutoDeviceSelector automatically selects the first available wearable device.
  val deviceSelector: DeviceSelector by lazy { AutoDeviceSelector() }
  private var deviceSelectorJob: Job? = null

  private var monitoringStarted = false
  private var recentErrorId = 0L
  private val deviceMonitoringJobs = mutableMapOf<DeviceIdentifier, Job>()
  private val deviceCompatibility = mutableMapOf<DeviceIdentifier, DeviceCompatibility>()

  // Custom Audio Managers
  private val audioPlayer = AudioPlayerManager(application)
  private val audioRecorder = AudioRecorderManager(application)
  private var audioProgressJob: Job? = null

  private fun startMonitoring() {
    if (monitoringStarted) {
      return
    }
    monitoringStarted = true

    // Monitor device selector for active device
    deviceSelectorJob = viewModelScope.launch {
      deviceSelector.activeDeviceFlow().collect { device ->
        Log.d("DATWearables", "Active device changed: $device")
        activeDeviceId = device
        _uiState.update { it.copy(hasActiveDevice = device != null) }
        updateFirmwareUpdateRequired()
      }
    }

    // This allows the app to react to registration changes (registered, unregistered, etc.)
    viewModelScope.launch {
      Wearables.registrationState.collect { value ->
        Log.d("DATWearables", "Registration state: $value")
        _uiState.update { it.copy(registrationState = value) }
      }
    }
    // This automatically updates when devices are discovered, connected, or disconnected
    viewModelScope.launch {
      Wearables.devices.collect { value ->
        Log.d("DATWearables", "Discovered devices: $value")
        _uiState.update { it.copy(devices = value.toList().toImmutableList()) }
        // Monitor device metadata for compatibility issues
        monitorDeviceCompatibility(value)
      }
    }
  }

  private var activeDeviceId: DeviceIdentifier? = null

  private fun monitorDeviceCompatibility(devices: Set<DeviceIdentifier>) {
    // Cancel monitoring jobs for devices that are no longer in the list
    val removedDevices = deviceMonitoringJobs.keys - devices
    removedDevices.forEach { deviceId ->
      deviceMonitoringJobs[deviceId]?.cancel()
      deviceMonitoringJobs.remove(deviceId)
      deviceCompatibility.remove(deviceId)
    }
    updateFirmwareUpdateRequired()

    // Start monitoring jobs only for new devices (not already being monitored)
    val newDevices = devices - deviceMonitoringJobs.keys
    newDevices.forEach { deviceId ->
      val job = viewModelScope.launch {
        Wearables.devicesMetadata[deviceId]?.collect { metadata ->
          Log.d(
              "DATWearables",
              "Device $deviceId metadata -> name: ${metadata.name}, compatibility: ${metadata.compatibility}, type: ${metadata.deviceType}, linkState: ${metadata.linkState}",
          )
          deviceCompatibility[deviceId] = metadata.compatibility
          updateFirmwareUpdateRequired()
          if (metadata.compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED) {
            val deviceName = metadata.name.ifEmpty { deviceId }
            setRecentError(application.getString(R.string.error_device_update_required, deviceName))
          }
        }
      }
      deviceMonitoringJobs[deviceId] = job
    }
  }

  fun startRegistration(activity: Activity) {
    Wearables.startRegistration(activity)
  }

  fun startUnregistration(activity: Activity) {
    Wearables.startUnregistration(activity)
  }

  fun openFirmwareUpdate(activity: Activity) {
    Wearables.openFirmwareUpdate(activity).onFailure { error, _ ->
      setRecentError(error.description)
    }
  }

  fun openDATGlassesAppUpdate(activity: Activity) {
    Wearables.openDATGlassesAppUpdate(activity).onFailure { error, _ ->
      setRecentError(error.description)
    }
  }

  fun showDebugMenu() {
    _uiState.update { it.copy(isDebugMenuVisible = true) }
  }

  fun hideDebugMenu() {
    _uiState.update { it.copy(isDebugMenuVisible = false) }
  }

  fun clearRecentError(errorId: Long) {
    _uiState.update { state ->
      if (state.recentError?.id == errorId) state.copy(recentError = null) else state
    }
  }

  internal fun setRecentError(error: String) {
    recentErrorId += 1
    _uiState.update { it.copy(recentError = RecentError(recentErrorId, error)) }
  }

  fun onPermissionsResult(permissionsResult: Map<String, Boolean>, onAllGranted: () -> Unit) {
    val granted = permissionsResult.entries.all { it.value }
    _uiState.update { it.copy(canRegister = granted) }
    if (granted) {
      onAllGranted()
      startMonitoring()
    } else {
      setRecentError(application.getString(R.string.error_permissions_required))
    }
  }

  private fun updateFirmwareUpdateRequired() {
    val active = activeDeviceId
    val isRequired = if (active != null && deviceCompatibility.containsKey(active)) {
      deviceCompatibility[active] == DeviceCompatibility.DEVICE_UPDATE_REQUIRED
    } else {
      deviceCompatibility.values.any { it == DeviceCompatibility.DEVICE_UPDATE_REQUIRED }
    }
    _uiState.update { it.copy(isFirmwareUpdateRequired = isRequired) }
  }

  fun setDatAppUpdateRequired(required: Boolean) {
    _uiState.update { it.copy(isDatAppUpdateRequired = required) }
  }

  // --- Custom Audio Recording & Playback Methods ---

  fun startAudioRecording() {
    if (_uiState.value.isRecordingAudio) return
    stopAudioPlayback()
    audioRecorder.startRecording()
    _uiState.update { it.copy(isRecordingAudio = true) }
  }

  fun stopAudioRecording() {
    val file = audioRecorder.stopRecording()
    val duration = file?.let { getAudioDuration(it.absolutePath) } ?: 0
    _uiState.update {
      it.copy(
        isRecordingAudio = false,
        lastAudioRecordingPath = file?.absolutePath,
        isPlayingAudio = false,
        isAudioPaused = false,
        currentAudioPositionMs = 0,
        totalAudioDurationMs = duration,
      )
    }
  }

  private fun startAudioProgressTracker() {
    audioProgressJob?.cancel()
    audioProgressJob = viewModelScope.launch {
      while (isActive && audioPlayer.isPlaying) {
        val currentPos = audioPlayer.currentPosition
        val totalDur = audioPlayer.duration
        _uiState.update {
          it.copy(
            currentAudioPositionMs = currentPos,
            totalAudioDurationMs = if (totalDur > 0) totalDur else it.totalAudioDurationMs,
          )
        }
        delay(100L)
      }
    }
  }

  fun playAudio() {
    val path = _uiState.value.lastAudioRecordingPath ?: return
    if (_uiState.value.isAudioPaused) {
      audioPlayer.resume()
      _uiState.update { it.copy(isPlayingAudio = true, isAudioPaused = false) }
      startAudioProgressTracker()
    } else {
      audioPlayer.play(path) {
        audioProgressJob?.cancel()
        _uiState.update {
          it.copy(
            isPlayingAudio = false,
            isAudioPaused = false,
            currentAudioPositionMs = 0,
          )
        }
      }
      val duration = audioPlayer.duration
      _uiState.update {
        it.copy(
          isPlayingAudio = true,
          isAudioPaused = false,
          totalAudioDurationMs = if (duration > 0) duration else it.totalAudioDurationMs,
        )
      }
      startAudioProgressTracker()
    }
  }

  fun pauseAudio() {
    audioPlayer.pause()
    audioProgressJob?.cancel()
    _uiState.update {
      it.copy(
        isPlayingAudio = false,
        isAudioPaused = true,
        currentAudioPositionMs = audioPlayer.currentPosition,
      )
    }
  }

  fun togglePlayPauseAudio() {
    if (_uiState.value.isPlayingAudio) {
      pauseAudio()
    } else {
      playAudio()
    }
  }

  fun seekAudio(positionMs: Int) {
    audioPlayer.seekTo(positionMs)
    _uiState.update { it.copy(currentAudioPositionMs = positionMs) }
  }

  fun forwardAudio(ms: Int = 5000) {
    val total = _uiState.value.totalAudioDurationMs
    val newPos = (_uiState.value.currentAudioPositionMs + ms).coerceAtMost(if (total > 0) total else Int.MAX_VALUE)
    seekAudio(newPos)
  }

  fun rewindAudio(ms: Int = 5000) {
    val newPos = (_uiState.value.currentAudioPositionMs - ms).coerceAtLeast(0)
    seekAudio(newPos)
  }

  fun stopAudioPlayback() {
    audioPlayer.stop()
    audioProgressJob?.cancel()
    _uiState.update {
      it.copy(
        isPlayingAudio = false,
        isAudioPaused = false,
        currentAudioPositionMs = 0,
      )
    }
  }

  fun playLastRecording() {
    playAudio()
  }

  fun stopPlayback() {
    stopAudioPlayback()
  }

  private fun getAudioDuration(filePath: String): Int {
    return try {
      val retriever = MediaMetadataRetriever()
      retriever.setDataSource(filePath)
      val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      retriever.release()
      time?.toIntOrNull() ?: 0
    } catch (e: Exception) {
      0
    }
  }

  override fun onCleared() {
    super.onCleared()
    audioProgressJob?.cancel()
    audioPlayer.release()
    audioRecorder.release()
    // Cancel all device monitoring jobs when ViewModel is cleared
    deviceMonitoringJobs.values.forEach { it.cancel() }
    deviceMonitoringJobs.clear()
    deviceSelectorJob?.cancel()
  }
}
