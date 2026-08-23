package com.cripta.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cripta.app.security.SessionManager
import com.cripta.app.ui.auth.AuthScreen
import com.cripta.app.ui.settings.SettingsScreen
import com.cripta.app.ui.vault.VaultScreen
import com.cripta.app.ui.viewer.ViewerScreen

@Composable
fun AppRoot(session: SessionManager, onAuthenticate: () -> Unit) {
    val locked by session.locked.collectAsState()

    if (locked) {
        AuthScreen(onAuthenticate = onAuthenticate)
        return
    }

    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = "vault",
        enterTransition = { fadeIn(tween(120)) },
        exitTransition = { fadeOut(tween(80)) },
        popEnterTransition = { fadeIn(tween(120)) },
        popExitTransition = { fadeOut(tween(80)) },
    ) {
        composable("vault") {
            VaultScreen(
                onOpenFile = { fileId -> nav.navigate("viewer/$fileId") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("viewer/{fileId}") { backStack ->
            val fileId = backStack.arguments?.getString("fileId").orEmpty()
            ViewerScreen(fileId = fileId, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
