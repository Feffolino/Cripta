package com.cripta.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavHostController
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
    Tab("download", "Download", Icons.Filled.Download),
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
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Physical camera-cutout insets read straight from the view — Compose's WindowInsets.displayCutout
    // reports 0 here (the cutout sits within the status-bar area), so tab content would still run
    // under the side camera. These give the real left/right camera width.
    //
    // The activity handles rotation itself (configChanges), so it is never recreated: the cutout has
    // to be re-read every time the layout changes, not once at composition. Reading it a single time
    // (the old approach) captured whatever orientation happened to be current and never updated — so
    // a landscape left/right inset leaked into portrait as a stray side margin, and a portrait 0
    // leaked into landscape leaving content under the side camera. An OnGlobalLayoutListener re-reads
    // the settled insets after each layout pass (including the one that follows an inset dispatch),
    // so the value tracks the real orientation instead of a stale one. We observe via the view tree
    // rather than setOnApplyWindowInsetsListener so we don't replace Compose's own insets listener.
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var cutoutStart by remember { mutableStateOf(0.dp) }
    var cutoutEnd by remember { mutableStateOf(0.dp) }
    DisposableEffect(view) {
        fun readCutout() {
            val i = androidx.core.view.ViewCompat.getRootWindowInsets(view)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            with(density) {
                cutoutStart = (i?.left ?: 0).toDp()
                cutoutEnd = (i?.right ?: 0).toDp()
            }
        }
        readCutout()
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener { readCutout() }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }

    fun onTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // A link shared into the app: jump to the Download tab so the user can pick a resolution.
    // (DownloadScreen reads and clears the pending link.)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sharedLinks = androidx.compose.runtime.remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            ctx.applicationContext, com.cripta.app.CriptaApp.AppEntryPoint::class.java,
        ).sharedLinkStore()
    }
    val pendingLink by sharedLinks.pending.collectAsState()
    // Tap on the "duplicate scan finished" notification: go to Settings, which opens the results.
    val dupStore = androidx.compose.runtime.remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            ctx.applicationContext, com.cripta.app.CriptaApp.AppEntryPoint::class.java,
        ).dupScanStore()
    }
    val openDup by dupStore.openRequested.collectAsState()
    androidx.compose.runtime.LaunchedEffect(openDup) {
        if (openDup && currentRoute != "settings") onTab("settings")
    }
    androidx.compose.runtime.LaunchedEffect(pendingLink) {
        if (pendingLink != null && currentRoute != "download") onTab("download")
    }

    Scaffold(
        // Each child screen has its own Scaffold that applies the status-bar inset to its
        // TopAppBar. If this outer Scaffold also consumed the top inset into `pad`, the content
        // (and thus the child TopAppBar) would be pushed down by the status bar height twice,
        // making every top bar look too tall / too low. Zero it here; children own the top inset.
        // The bottom NavigationBar still handles its own navigation-bar inset internally.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Portrait: bottom navigation bar. Landscape: a side rail instead (see content), so the
        // short landscape height isn't eaten by a bottom bar.
        bottomBar = {
            if (showBar && !landscape) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onTab(tab.route) },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        // Content fills edge-to-edge (incl. the display cutout) so the system-bar / cutout strips
        // show whatever screen is behind them — the app surface on the tabs, black in the player.
        Row(
            Modifier.padding(pad).fillMaxSize().then(
                // Keep the tab screens (rail + content) clear of the side camera cutout; the
                // fullscreen player handles its own insets.
                if (showBar) Modifier.padding(start = cutoutStart, end = cutoutEnd) else Modifier,
            ),
        ) {
            if (showBar && landscape) {
                // Inset the rail from the left edge / status bar so its labels don't touch it.
                NavigationRail(
                    windowInsets = WindowInsets.systemBars.only(
                        WindowInsetsSides.Start + WindowInsetsSides.Vertical,
                    ),
                ) {
                    tabs.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onTab(tab.route) },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.weight(1f),
                // Subtle scale + fade so entering a screen feels like it comes forward, not a flat
                // cut. Exit is quicker than enter so navigation feels responsive.
                enterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220)) },
                exitTransition = { fadeOut(tween(140)) + scaleOut(targetScale = 1.02f, animationSpec = tween(140)) },
                popEnterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 1.02f, animationSpec = tween(220)) },
                popExitTransition = { fadeOut(tween(140)) + scaleOut(targetScale = 0.97f, animationSpec = tween(140)) },
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
                    onOpenFavorites = { nav.navigate("favorites") },
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
                FavoritesScreen(
                    onOpenFile = { nav.navigate("viewer/$it") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("download") {
                com.cripta.app.ui.download.DownloadScreen(onOpenFile = { nav.navigate("viewer/$it") })
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
}
