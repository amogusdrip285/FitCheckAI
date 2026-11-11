package com.fitcheckai.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitcheckai.core.model.nav.Routes
import com.fitcheckai.feature.insights.insightsGraph
import com.fitcheckai.feature.onboarding.onboardingGraph
import com.fitcheckai.feature.today.todayGraph
import com.fitcheckai.feature.wardrobe.wardrobeGraph

private data class TopLevelTab(val route: String, val routePrefix: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TopLevelTab(Routes.TODAY, Routes.TODAY, "Hôm nay", Icons.Outlined.WbSunny),
    TopLevelTab(Routes.WARDROBE, Routes.WARDROBE, "Tủ đồ", Icons.Outlined.Checkroom),
    TopLevelTab(Routes.INSIGHTS, Routes.INSIGHTS, "Insights", Icons.Outlined.Insights),
)

@Composable
fun FitCheckApp(viewModel: MainViewModel = hiltViewModel()) {
    val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
    val start = startDestination ?: return // splash ngắn trong lúc đọc profile

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = remember(currentRoute) { currentRoute != null && currentRoute != Routes.ONBOARDING }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute?.startsWith(tab.routePrefix) == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            onboardingGraph(navController)
            todayGraph(navController)
            wardrobeGraph(navController)
            insightsGraph(navController)
        }
    }
}
