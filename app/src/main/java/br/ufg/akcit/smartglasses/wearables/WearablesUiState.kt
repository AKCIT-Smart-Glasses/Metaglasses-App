/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// WearablesUiState - DAT API State Management
//
// This data class aggregates DAT API state for the UI layer

package br.ufg.akcit.smartglasses.wearables

import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class WearablesUiState(
    val registrationState: RegistrationState = RegistrationState.UNAVAILABLE,
    val devices: ImmutableList<DeviceIdentifier> = persistentListOf(),
    val recentError: String? = null,
    val isStreaming: Boolean = false,
    val isDebugMenuVisible: Boolean = false,
    val isGettingStartedSheetVisible: Boolean = false,
    val isFirmwareUpdateRequired: Boolean = false,
    val isDatAppUpdateRequired: Boolean = false,
    val hasActiveDevice: Boolean = false,
    val canRegister: Boolean = false,
    val isRecordingAudio: Boolean = false,
    val lastAudioRecordingPath: String? = null,
    val isPlayingAudio: Boolean = false,
    val isAudioPaused: Boolean = false,
    val currentAudioPositionMs: Int = 0,
    val totalAudioDurationMs: Int = 0,
) {
  val isRegistered: Boolean =
      registrationState == RegistrationState.REGISTERED ||
          registrationState == RegistrationState.UNREGISTERING

  val isRegistering: Boolean = registrationState == RegistrationState.REGISTERING

  val canStartRegistration: Boolean = canRegister && !isRegistering
}
