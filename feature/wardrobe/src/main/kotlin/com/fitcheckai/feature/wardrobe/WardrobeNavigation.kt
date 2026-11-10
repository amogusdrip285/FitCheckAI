package com.fitcheckai.feature.wardrobe

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitcheckai.core.model.nav.Routes

/** Điểm vào duy nhất của feature — `app` gọi đúng 1 lần. Track A thêm màn hình mới ở đây. */
fun NavGraphBuilder.wardrobeGraph(navController: NavController) {
    composable(Routes.WARDROBE) {
        WardrobeScreen(
            onAddItem = { navController.navigate(Routes.WARDROBE_ADD) },
            onOpenItem = { navController.navigate(Routes.wardrobeItem(it)) },
        )
    }
    composable(Routes.WARDROBE_ADD) {
        AddItemPlaceholderScreen(onBack = navController::popBackStack)
    }
    composable(
        route = Routes.WARDROBE_ITEM_PATTERN,
        arguments = listOf(navArgument(Routes.ARG_ITEM_ID) { type = NavType.StringType }),
    ) {
        ItemDetailScreen(
            onBack = navController::popBackStack,
            onStyleAround = { navController.navigate(Routes.today(it)) },
        )
    }
}
