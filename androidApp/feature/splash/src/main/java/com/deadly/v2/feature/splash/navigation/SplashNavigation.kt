package com.deadly.v2.feature.splash.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.deadly.v2.feature.splash.SplashScreen

/**
 * Navigation graph for splash feature
 */
fun NavGraphBuilder.splashGraph(navController: NavController) {
    composable("splash") {
        SplashScreen(
            onSplashComplete = {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        )
    }
}