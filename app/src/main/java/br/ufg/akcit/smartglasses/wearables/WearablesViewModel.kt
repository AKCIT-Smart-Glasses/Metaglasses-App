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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import android.media.MediaMetadataRetriever
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WearablesViewModel(application: Application) : AndroidViewModel(application) {
  private val _uiState = MutableStateFlow(WearablesUiState())
  val uiState: StateFlow<WearablesUiState> = _uiState.asStateFlow()

  // AutoDeviceSelector automatically selects the first available wearable device.
  val deviceSelector: DeviceSelector by lazy { AutoDeviceSelector() }
  private var deviceSelectorJob: Job? = null

  private val audioPlayer = AudioPlayerManager(application)

  private var monitoringStarted = false
  private val deviceMonitoringJobs = mutableMapOf<DeviceIdentifier, Job>()
  private val deviceCompatibility = mutableMapOf<DeviceIdentifier, DeviceCompatibility>()

  private fun startMonitoring() {
    if (monitoringStarted) {
      return
    }
    monitoringStarted = true

    // Monitor device selector for active device
    deviceSelectorJob = viewModelScope.launch {
      deviceSelector.activeDeviceFlow().collect { device ->
        _uiState.update { it.copy(hasActiveDevice = device != null) }
      }
    }

    // This allows the app to react to registration changes (registered, unregistered, etc.)
    viewModelScope.launch {
      Wearables.registrationState.collect { value ->
        val previousState = _uiState.value.registrationState
        val showGettingStartedSheet =
            value == RegistrationState.REGISTERED && previousState == RegistrationState.REGISTERING
        _uiState.update {
          it.copy(registrationState = value, isGettingStartedSheetVisible = showGettingStartedSheet)
        }
      }
    }
    // This automatically updates when devices are discovered, connected, or disconnected
    viewModelScope.launch {
      Wearables.devices.collect { value ->
        _uiState.update { it.copy(devices = value.toList().toImmutableList()) }
        // Monitor device metadata for compatibility issues
        monitorDeviceCompatibility(value)
      }
    }
  }

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
          deviceCompatibility[deviceId] = metadata.compatibility
          updateFirmwareUpdateRequired()
          if (metadata.compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED) {
            val deviceName = metadata.name.ifEmpty { deviceId }
            setRecentError("Device '$deviceName' requires an update to work with this app")
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

  fun navigateToStreaming(onRequestWearablesPermission: suspend (Permission) -> PermissionStatus) {
    viewModelScope.launch {
      val permission = Permission.CAMERA // Camera permission is required for streaming
      val result = Wearables.checkPermissionStatus(permission)

      // Handle the result
      result.onFailure { error, _ ->
        setRecentError("Permission check error: ${error.description}")
        return@launch
      }

      val permissionStatus = result.getOrNull()
      if (permissionStatus == PermissionStatus.Granted) {
        _uiState.update { it.copy(isStreaming = true) }
        return@launch
      }

      // Request permission
      val requestedPermissionStatus = onRequestWearablesPermission(permission)
      when (requestedPermissionStatus) {
        PermissionStatus.Denied -> {
          setRecentError("Permission denied")
        }
        PermissionStatus.Granted -> {
          _uiState.update { it.copy(isStreaming = true) }
        }
      }
    }
  }

  fun navigateToDeviceSelection() {
    _uiState.update { it.copy(isStreaming = false) }
  }

  fun showDebugMenu() {
    _uiState.update { it.copy(isDebugMenuVisible = true) }
  }

  fun hideDebugMenu() {
    _uiState.update { it.copy(isDebugMenuVisible = false) }
  }

  fun clearRecentError() {
    _uiState.update { it.copy(recentError = null) }
  }

  internal fun setRecentError(error: String) {
    _uiState.update { it.copy(recentError = error) }
  }

  internal fun setDatAppUpdateRequired(required: Boolean) {
    _uiState.update { it.copy(isDatAppUpdateRequired = required) }
  }

  fun onPermissionsResult(permissionsResult: Map<String, Boolean>, onAllGranted: () -> Unit) {
    val granted = permissionsResult.entries.all { it.value }
    _uiState.update { it.copy(canRegister = granted) }
    if (granted) {
      onAllGranted()
      startMonitoring()
    } else {
      _uiState.update {
        it.copy(recentError = "Allow All Permissions (Bluetooth, Bluetooth Connect, Internet)")
      }
    }
  }

  fun showGettingStartedSheet() {
    _uiState.update { it.copy(isGettingStartedSheetVisible = true) }
  }

  fun hideGettingStartedSheet() {
    _uiState.update { it.copy(isGettingStartedSheetVisible = false) }
  }

  override fun onCleared() {
    super.onCleared()
    // Cancel all device monitoring jobs when ViewModel is cleared
    audioProgressJob?.cancel()
    audioPlayer.release()
    audioRecorder.release()
    deviceMonitoringJobs.values.forEach { it.cancel() }
    deviceMonitoringJobs.clear()
    deviceSelectorJob?.cancel()
  }

  private fun updateFirmwareUpdateRequired() {
    val isRequired =
        deviceCompatibility.values.any { it == DeviceCompatibility.DEVICE_UPDATE_REQUIRED }
    _uiState.update { it.copy(isFirmwareUpdateRequired = isRequired) }
  }

  private val audioRecorder = AudioRecorderManager(application)
  private var audioProgressJob: Job? = null

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
}
