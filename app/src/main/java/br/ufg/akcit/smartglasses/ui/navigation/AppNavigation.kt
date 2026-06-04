package br.ufg.akcit.smartglasses.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.ufg.akcit.smartglasses.ui.screens.ExperimentalFeaturesScreen
import br.ufg.akcit.smartglasses.ui.screens.NonStreamScreen
import br.ufg.akcit.smartglasses.wearables.WearablesViewModel
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus

@Composable
fun AppNavigation(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
) {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "home") {
        composable("home") {
            NonStreamScreen(
                viewModel = viewModel,
                onRequestWearablesPermission = onRequestWearablesPermission,
                navController = navController
            )
        }
        composable("features") {
            ExperimentalFeaturesScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}
