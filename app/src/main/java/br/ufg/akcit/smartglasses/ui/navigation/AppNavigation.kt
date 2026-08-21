package br.ufg.akcit.smartglasses.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.ufg.akcit.smartglasses.ui.screens.CameraScreen
import br.ufg.akcit.smartglasses.ui.screens.ExperimentalFeaturesScreen
import br.ufg.akcit.smartglasses.wearables.WearablesViewModel
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus

@Composable
fun AppNavigation(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    onRequestRecordAudioPermission: suspend () -> Boolean,
) {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen(
                wearablesViewModel = viewModel,
                onRequestWearablesPermission = onRequestWearablesPermission,
                onRequestRecordAudioPermission = onRequestRecordAudioPermission,
                onNavigateToFeatures = { navController.navigate("features") },
            )
        }
        composable("features") {
            ExperimentalFeaturesScreen(
                navController = navController,
                viewModel = viewModel,
            )
        }
    }
}
