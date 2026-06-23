package com.menulens.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.menulens.app.ui.screens.DetailScreen
import com.menulens.app.ui.screens.PaywallScreen
import com.menulens.app.ui.screens.ProcessingScreen
import com.menulens.app.ui.screens.ResultsScreen
import com.menulens.app.ui.screens.ScanScreen
import com.menulens.app.ui.screens.ShowToStaffScreen
import com.menulens.app.viewmodel.ResultsNavEvent
import com.menulens.app.viewmodel.ResultsViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val resultsViewModel: ResultsViewModel = viewModel()
    val uiState = resultsViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        resultsViewModel.navEvents.collect { event ->
            when (event) {
                is ResultsNavEvent.OpenDetail -> navController.navigate(Route.Detail.create(event.itemId))
                is ResultsNavEvent.OpenShowToStaff -> navController.navigate(Route.ShowToStaff.create(event.itemId))
                ResultsNavEvent.OpenPaywall -> navController.navigate(Route.Paywall.value)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Scan.value
    ) {
        composable(Route.Scan.value) {
            ScanScreen(
                onMenuImageReady = { imageBytes ->
                    resultsViewModel.startNewScanSession()
                    resultsViewModel.queueScanImage(imageBytes)
                    navController.navigate(Route.Processing.value)
                }
            )
        }
        composable(Route.Processing.value) {
            ProcessingScreen(
                state = uiState.value,
                onStartProcessing = resultsViewModel::processPendingScan,
                onRetry = resultsViewModel::processPendingScan,
                onBackToScan = {
                    navController.popBackStack(Route.Scan.value, false)
                },
                onProcessingComplete = {
                    navController.navigateSingleTop(Route.Results.value)
                }
            )
        }
        composable(Route.Results.value) {
            ResultsScreen(
                state = uiState.value,
                onItemTap = resultsViewModel::onResultItemSelected
            )
        }
        composable(
            route = Route.Detail.value,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) {
            val itemId = it.arguments?.getString("itemId").orEmpty()
            DetailScreen(
                state = uiState.value,
                itemId = itemId,
                onBack = { navController.popBackStack() },
                onReveal = { resultsViewModel.onRevealFromDetail(itemId) },
                onShowToStaff = { resultsViewModel.onShowToStaff(itemId) },
                onRetryImage = { resultsViewModel.retryDishImage(itemId) }
            )
        }
        composable(
            route = Route.ShowToStaff.value,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) {
            val itemId = it.arguments?.getString("itemId").orEmpty()
            val item = uiState.value.itemById(itemId)
            ShowToStaffScreen(
                jpText = item?.jpText.orEmpty(),
                priceText = item?.priceText
            )
        }
        composable(Route.Paywall.value) {
            PaywallScreen(
                isPro = uiState.value.isPro,
                creditsRemaining = uiState.value.creditsRemainingToday,
                onEnablePro = { resultsViewModel.setProEnabled(true) },
                onDisablePro = { resultsViewModel.setProEnabled(false) },
                onRestore = resultsViewModel::restorePurchasesStub
            )
        }
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(Route.Processing.value) { inclusive = true }
        launchSingleTop = true
    }
}

