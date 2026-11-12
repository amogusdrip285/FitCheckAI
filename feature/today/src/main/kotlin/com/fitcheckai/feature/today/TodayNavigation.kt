package com.fitcheckai.feature.today

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitcheckai.core.model.nav.Routes

/** Điểm vào duy nhất của feature — `app` gọi đúng 1 lần. */
fun NavGraphBuilder.todayGraph(navController: NavController) {
    composable(
        route = Routes.TODAY_PATTERN,
        arguments = listOf(
            navArgument(Routes.ARG_FOCUS_ITEM_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        TodayScreen(onOpenItem = { navController.navigate(Routes.wardrobeItem(it)) })
    }
}
