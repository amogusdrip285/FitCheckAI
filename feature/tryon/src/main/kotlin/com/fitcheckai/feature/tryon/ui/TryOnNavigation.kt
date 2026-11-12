package com.fitcheckai.feature.tryon.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.Composable

/**
 * Navigation seam owned by Track C (`C-209`) so Track D can wire `:feature:tryon` into
 * `FitCheckApp.kt` (`NavHost`) with zero business-logic coupling.
 */
object TryOnDestinations {
    const val ARG_OUTFIT_ID = "outfitId"
    const val ARG_SNAPSHOT_ID = "snapshotId"
    const val ROUTE_PATTERN = "tryon/{$ARG_OUTFIT_ID}?$ARG_SNAPSHOT_ID={$ARG_SNAPSHOT_ID}"

    fun buildRoute(outfitId: String, snapshotId: String? = null): String =
        if (snapshotId == null) {
            "tryon/$outfitId"
        } else {
            "tryon/$outfitId?$ARG_SNAPSHOT_ID=$snapshotId"
        }
}

fun NavGraphBuilder.tryOnGraph(
    content: @Composable (outfitId: String, snapshotId: String?) -> Unit,
) {
    composable(
        route = TryOnDestinations.ROUTE_PATTERN,
        arguments = listOf(
            navArgument(TryOnDestinations.ARG_OUTFIT_ID) { type = NavType.StringType },
            navArgument(TryOnDestinations.ARG_SNAPSHOT_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val outfitId = backStackEntry.arguments?.getString(TryOnDestinations.ARG_OUTFIT_ID).orEmpty()
        val snapshotId = backStackEntry.arguments?.getString(TryOnDestinations.ARG_SNAPSHOT_ID)
        content(outfitId, snapshotId)
    }
}
