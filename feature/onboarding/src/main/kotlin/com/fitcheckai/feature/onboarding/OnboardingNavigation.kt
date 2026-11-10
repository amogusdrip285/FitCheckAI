package com.fitcheckai.feature.onboarding

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fitcheckai.core.model.nav.Routes

/** Điểm vào duy nhất của feature — `app` gọi đúng 1 lần. */
fun NavGraphBuilder.onboardingGraph(navController: NavController) {
    composable(Routes.ONBOARDING) {
        OnboardingScreen(
            onFinished = {
                navController.navigate(Routes.TODAY) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            },
        )
    }
}
