package com.fitcheckai.feature.onboarding

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fitcheckai.core.model.nav.Routes
import com.fitcheckai.feature.onboarding.profile.ProfileMode
import com.fitcheckai.feature.onboarding.profile.ProfileRoute

/** Entry point for onboarding + profile edit — `app` calls it exactly once. */
fun NavGraphBuilder.onboardingGraph(navController: NavController) {
    composable(Routes.ONBOARDING) {
        ProfileRoute(
            mode = ProfileMode.ONBOARDING,
            onDone = {
                navController.navigate(Routes.TODAY) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            },
        )
    }
    composable(Routes.PROFILE) {
        ProfileRoute(mode = ProfileMode.EDIT, onDone = {})
    }
}
