package com.fitcheckai.feature.insights

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fitcheckai.core.model.nav.Routes

/** Điểm vào duy nhất của feature — `app` gọi đúng 1 lần. */
fun NavGraphBuilder.insightsGraph(navController: NavController) {
    composable(Routes.INSIGHTS) {
        InsightsScreen(onStyleAround = { navController.navigate(Routes.today(it)) })
    }
}
