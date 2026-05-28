package com.notmysni.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.notmysni.ui.main.MainScreen
import com.notmysni.ui.settings.SettingsScreen
import com.notmysni.ui.sni.SniSelectorScreen
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination

@Composable
fun NotMySniApp() {
    val viewModel: NotMySniViewModel = hiltViewModel()
    val sniState by viewModel.sniState.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(text = destination.iconLabel) },
                        label = { Text(text = destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Home.route) {
                MainScreen(activeSniHost = sniState.selectedHostname)
            }
            composable(AppDestination.Sni.route) {
                SniSelectorScreen(
                    selectedHostname = sniState.selectedHostname,
                    customHosts = sniState.customHosts,
                    onSelectedHostnameChange = viewModel::onSelectedHostnameChange
                )
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    settings = settingsState,
                    onSettingsChange = viewModel::onSettingsChange
                )
            }
        }
    }
}
