/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.ufg.akcit.smartglasses.R
import br.ufg.akcit.smartglasses.ui.components.SwitchButton
import br.ufg.akcit.smartglasses.voice.VoiceSessionManager
import br.ufg.akcit.smartglasses.voice.VoiceSessionState
import br.ufg.akcit.smartglasses.wearables.WearablesViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import br.ufg.akcit.smartglasses.camera.CameraViewModel
import br.ufg.akcit.smartglasses.elo.session.EloSessionService
import br.ufg.akcit.smartglasses.elo.turn.PhotoCapture
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.launch

@Composable
fun VoiceAssistantScreen(
    wearablesViewModel: WearablesViewModel,
    onRequestRecordAudioPermission: suspend () -> Boolean,
    onNavigateToCamera: () -> Unit,
    onNavigateToFeatures: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    cameraViewModel: CameraViewModel = viewModel(
        factory =
            CameraViewModel.Factory(
                application = (LocalActivity.current as ComponentActivity).application,
                wearablesViewModel = wearablesViewModel,
            ),
    ),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wearablesUi by wearablesViewModel.uiState.collectAsStateWithLifecycle()

    val sessionManager = remember {
        VoiceSessionManager(context).apply {
            photoCapture = PhotoCapture { cameraViewModel.capturePhotoForElo() }
        }
    }
    val voiceState by sessionManager.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        EloSessionService.start(context)
        onDispose {
            sessionManager.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Bar with 4-option menu
            AssistantTopBar(
                isDisconnectEnabled = wearablesUi.registrationState == RegistrationState.REGISTERED,
                onDisconnect = {
                    sessionManager.stopSession()
                    wearablesViewModel.startUnregistration(context as androidx.activity.ComponentActivity)
                },
                onNavigateToCamera = onNavigateToCamera,
                onNavigateToFeatures = onNavigateToFeatures,
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Audio routing device badge
                AudioDeviceBadge(isGlassesMic = voiceState.isGlassesMicActive)

            Spacer(modifier = Modifier.weight(1f))

            // Center Interactive Button (Criterion 6: Tap-to-Talk)
            CentralAssistantButton(
                state = voiceState.state,
                isSessionActive = voiceState.isSessionActive,
                amplitude = voiceState.amplitudeLevel,
                onClick = {
                    scope.launch {
                        val granted = onRequestRecordAudioPermission()
                        if (granted) {
                            sessionManager.onMainButtonClicked()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status Description
            StatusMessageSection(
                state = voiceState.state,
                partialTranscription = voiceState.partialTranscription,
                finalCommand = voiceState.finalCommand,
                assistantResponse = voiceState.assistantResponse,
                errorMessage = voiceState.errorMessage,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Hands-Free Wake Word Toggle (Extra: "Olá Óculos")
            HandsFreeToggleCard(
                isHandsFreeActive = voiceState.isHandsFreeWakeWordActive,
                onToggle = {
                    scope.launch {
                        val granted = onRequestRecordAudioPermission()
                        if (granted) {
                            sessionManager.toggleHandsFreeWakeWord()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AssistantTopBar(
    isDisconnectEnabled: Boolean,
    onDisconnect: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToFeatures: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.assistant_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.debug_menu_description),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                // 1. Câmera
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_camera)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onNavigateToCamera()
                    },
                )

                // 2. Recursos Experimentais
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_experimental_features)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onNavigateToFeatures()
                    },
                )

                // 3. Configurações do Servidor
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_settings)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onNavigateToSettings()
                    },
                )

                HorizontalDivider()

                // 4. Desconectar Óculos
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.menu_disconnect),
                            color = if (isDisconnectEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = if (isDisconnectEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    enabled = isDisconnectEnabled,
                    onClick = {
                        menuExpanded = false
                        onDisconnect()
                    },
                )
            }
        }
    }
}

@Composable
private fun AudioDeviceBadge(isGlassesMic: Boolean) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (isGlassesMic) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (isGlassesMic) Icons.Default.Headphones else Icons.Default.Mic,
                contentDescription = null,
                tint = if (isGlassesMic) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (isGlassesMic) {
                    stringResource(R.string.assistant_mic_glasses_active)
                } else {
                    stringResource(R.string.assistant_mic_phone_active)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isGlassesMic) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CentralAssistantButton(
    state: VoiceSessionState,
    isSessionActive: Boolean,
    amplitude: Float,
    onClick: () -> Unit,
) {
    val isListeningCommand = state == VoiceSessionState.LISTENING_COMMAND
    val isListeningWakeWord = state == VoiceSessionState.LISTENING_WAKE_WORD
    val isListening = isListeningCommand || isListeningWakeWord
    val isProcessing = state == VoiceSessionState.PROCESSING
    val isSpeaking = state == VoiceSessionState.SPEAKING

    // Infinite pulsating animation for listening waves
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isSpeaking) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isListening || isSpeaking) 0.05f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val buttonColor by animateColorAsState(
        targetValue = when (state) {
            VoiceSessionState.ERROR -> MaterialTheme.colorScheme.errorContainer
            VoiceSessionState.SPEAKING -> MaterialTheme.colorScheme.tertiaryContainer
            VoiceSessionState.LISTENING_COMMAND -> MaterialTheme.colorScheme.primary
            VoiceSessionState.LISTENING_WAKE_WORD -> MaterialTheme.colorScheme.primaryContainer
            VoiceSessionState.PROCESSING -> MaterialTheme.colorScheme.secondaryContainer
            VoiceSessionState.INITIALIZING -> MaterialTheme.colorScheme.surfaceContainerHighest
            VoiceSessionState.IDLE -> MaterialTheme.colorScheme.primary
        },
        label = "button_color"
    )

    val contentColor by animateColorAsState(
        targetValue = when (state) {
            VoiceSessionState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
            VoiceSessionState.SPEAKING -> MaterialTheme.colorScheme.onTertiaryContainer
            VoiceSessionState.LISTENING_COMMAND -> MaterialTheme.colorScheme.onPrimary
            VoiceSessionState.LISTENING_WAKE_WORD -> MaterialTheme.colorScheme.onPrimaryContainer
            VoiceSessionState.PROCESSING -> MaterialTheme.colorScheme.onSecondaryContainer
            VoiceSessionState.INITIALIZING -> MaterialTheme.colorScheme.onSurface
            VoiceSessionState.IDLE -> MaterialTheme.colorScheme.onPrimary
        },
        label = "content_color"
    )

    val accessibilityDesc = when (state) {
        VoiceSessionState.LISTENING_COMMAND -> stringResource(R.string.assistant_btn_finish_listening)
        VoiceSessionState.SPEAKING -> stringResource(R.string.assistant_btn_stop_speaking)
        else -> stringResource(R.string.assistant_btn_talk)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(260.dp)
    ) {
        // Outer pulsing rings (Visual feedback reacting to voice amplitude)
        if (isListening || isSpeaking) {
            val dynamicScale = pulseScale + (amplitude * 0.35f)
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(dynamicScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
            )
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .scale(1f + (amplitude * 0.45f))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            )
        }

        // Main Circular Button
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = accessibilityDesc
                },
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                VoiceSessionState.INITIALIZING, VoiceSessionState.PROCESSING -> {
                    CircularProgressIndicator(
                        color = contentColor,
                        modifier = Modifier.size(48.dp),
                    )
                }
                VoiceSessionState.SPEAKING -> {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(64.dp),
                    )
                }
                VoiceSessionState.LISTENING_COMMAND -> {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(64.dp),
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMessageSection(
    state: VoiceSessionState,
    partialTranscription: String,
    finalCommand: String?,
    assistantResponse: String?,
    errorMessage: String?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val titleText = when (state) {
            VoiceSessionState.IDLE -> stringResource(R.string.assistant_state_ready)
            VoiceSessionState.INITIALIZING -> stringResource(R.string.assistant_state_initializing)
            VoiceSessionState.LISTENING_WAKE_WORD -> stringResource(R.string.assistant_state_listening_wakeword)
            VoiceSessionState.LISTENING_COMMAND -> stringResource(R.string.assistant_state_listening_command)
            VoiceSessionState.PROCESSING -> stringResource(R.string.assistant_state_processing)
            VoiceSessionState.SPEAKING -> stringResource(R.string.assistant_state_speaking)
            VoiceSessionState.ERROR -> errorMessage ?: stringResource(R.string.assistant_state_error)
        }

        val subtitleText = when (state) {
            VoiceSessionState.IDLE -> stringResource(R.string.assistant_state_ready_desc)
            VoiceSessionState.LISTENING_COMMAND -> stringResource(R.string.assistant_state_listening_command_desc)
            VoiceSessionState.LISTENING_WAKE_WORD -> stringResource(R.string.assistant_hands_free_desc)
            else -> null
        }

        Text(
            text = titleText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        if (subtitleText != null) {
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }

        // Live partial transcription of user question
        if (partialTranscription.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.assistant_last_command_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "\"$partialTranscription\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Assistant response card
        if (!assistantResponse.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.assistant_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = assistantResponse,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun HandsFreeToggleCard(
    isHandsFreeActive: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.assistant_hands_free_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.assistant_hands_free_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = isHandsFreeActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}
