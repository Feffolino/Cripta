package com.cripta.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cripta.app.security.SessionManager
import com.cripta.app.ui.auth.AuthScreen
import com.cripta.app.ui.favorites.FavoritesScreen
import com.cripta.app.ui.home.HomeScreen
import com.cripta.app.ui.settings.SettingsScreen
import com.cripta.app.ui.vault.VaultScreen
import com.cripta.app.ui.viewer.ViewerScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("folders", "Cartelle", Icons.Filled.Folder),
    Tab("favorites", "Preferiti", Icons.Filled.Star),
    Tab("settings", "Impostazioni", Icons.Filled.Settings),
)

@Composable
fun AppRoot(session: SessionManager, onAuthenticate: () -> Unit) {
    val locked by session.locked.collectAsState()
    if (locked) {
        AuthScreen(onAuthenticate = onAuthenticate)
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in tabs.map { it.route }

    Scaffold(
        // Each child screen has its own Scaffold that applies the status-bar inset to its
        // TopAppBar. If this outer Scaffold also consumed the top inset into `pad`, the content
        // (and thus the child TopAppBar) would be pushed down by the status bar height twice,
        // making every top bar look too tall / too low. Zero it here; children own the top inset.
        // The bottom NavigationBar still handles its own navigation-bar inset internally.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(120)) },
            exitTransition = { fadeOut(tween(80)) },
            popEnterTransition = { fadeIn(tween(120)) },
            popExitTransition = { fadeOut(tween(80)) },
        ) {
            composable("home") {
                HomeScreen(
                    onOpenFile = { nav.navigate("viewer/$it") },
                    onOpenFolders = {
                        // Switch to the Cartelle tab with the same semantics as the bottom bar,
                        // so the back stack stays consistent and the Home tab keeps working.
                        nav.navigate("folders") {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onLock = { session.lock() },
                )
            }
            composable("folders") {
                VaultScreen(
                    onOpenFile = { nav.navigate("viewer/$it") },
                    onNewNote = { nav.navigate("note/new") },
                )
            }
            composable("favorites") {
                FavoritesScreen(onOpenFile = { nav.navigate("viewer/$it") })
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
            composable("viewer/{fileId}") { entry ->
                ViewerScreen(
                    fileId = entry.arguments?.getString("fileId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onEditNote = { nav.navigate("note/$it") },
                )
            }
            composable("note/new") {
                com.cripta.app.ui.note.NoteEditorScreen(fileId = null, onBack = { nav.popBackStack() })
            }
            composable("note/{id}") { entry ->
                com.cripta.app.ui.note.NoteEditorScreen(fileId = entry.arguments?.getString("id"), onBack = { nav.popBackStack() })
            }
        }
    }
}
