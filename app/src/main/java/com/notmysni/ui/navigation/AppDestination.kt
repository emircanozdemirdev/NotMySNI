package com.notmysni.ui.navigation

enum class AppDestination(
    val route: String,
    val label: String,
    val iconLabel: String
) {
    Home("home", "Home", "H"),
    Sni("sni", "SNI", "S"),
    Settings("settings", "Settings", "G")
}
