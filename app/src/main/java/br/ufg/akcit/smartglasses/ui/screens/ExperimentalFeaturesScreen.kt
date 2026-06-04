package br.ufg.akcit.smartglasses.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.ufg.akcit.smartglasses.ui.components.SwitchButton
import br.ufg.akcit.smartglasses.wearables.WearablesViewModel

@Composable
fun ExperimentalFeaturesScreen(
    viewModel: WearablesViewModel,
    navController: NavController,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Experimental Features",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            AudioRecordButton(
                isRecording = uiState.isRecordingAudio,
                onStartRecording = { viewModel.startAudioRecording() },
                onStopRecording = { viewModel.stopAudioRecording() },
            )

            uiState.lastAudioRecordingPath?.let { path ->
                Text(
                    text = "Último arquivo: ${path.substringAfterLast("/")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AudioRecordButton(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwitchButton(
        label = if (isRecording) "Parar Gravação" else "Gravar Áudio",
        onClick = if (isRecording) onStopRecording else onStartRecording,
        modifier = modifier,
    )
}
