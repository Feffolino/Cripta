package com.cripta.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cripta.app.security.SessionManager
import com.cripta.app.ui.auth.AuthScreen
import com.cripta.app.ui.auth.AuthUiState
import com.cripta.app.ui.favorites.FavoritesScreen
import com.cripta.app.ui.home.HomeScreen
import com.cripta.app.ui.settings.SettingsScreen
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.vault.VaultScreen
import com.cripta.app.ui.viewer.ViewerScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** Real left/right camera-cutout widths (Compose's displayCutout insets can report 0 here), for
 *  fullscreen screens such as the player that lay out their own chrome. */
data class SideCutout(val start: androidx.compose.ui.unit.Dp = 0.dp, val end: androidx.compose.ui.unit.Dp = 0.dp)
val LocalSideCutout = androidx.compose.runtime.compositionLocalOf { SideCutout() }

/**
 * True while the vault is locked. The screens stay composed under the lock screen (so folder
 * path, scroll position and unsaved text survive a lock) but are neither drawn nor interactive;
 * a screen doing work of its own (e.g. playback) can read this to pause.
 */
val LocalVaultLocked = androidx.compose.runtime.staticCompositionLocalOf { false }

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("folders", "Cartelle", Icons.Filled.Folder),
    Tab("download", "Download", Icons.Filled.Download),
    Tab("settings", "Impostazioni", Icons.Filled.Settings),
)
private val tabRoutes = tabs.map { it.route }.toSet()

/** Tab ↔ tab: siblings, so a fade-through (no depth). Anything else is a push/pop in depth. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

// Fade-through between tabs: the old one leaves fast, the new one arrives after it, barely scaled.
private val tabEnter: EnterTransition =
    fadeIn(tween(210, 90, Motion.EaseOutQuart)) + scaleIn(tween(210, 90, Motion.EaseOutQuart), initialScale = 0.98f)
private val tabExit: ExitTransition = fadeOut(tween(90, 0, Motion.EaseOutQuart))

// Z-axis for pushed screens (viewer, note, favourites…): the new screen comes forward from
// slightly behind, the old one recedes towards the viewer. Pop is the exact reverse.
private val pushEnter: EnterTransition =
    fadeIn(Motion.enter(Motion.LONG)) + scaleIn(Motion.enter(Motion.LONG), initialScale = 0.94f)
private val pushExit: ExitTransition =
    fadeOut(Motion.exit(Motion.LONG)) + scaleOut(Motion.exit(Motion.LONG), targetScale = 1.03f)
private val popEnter: EnterTransition =
    fadeIn(Motion.enter(Motion.LONG)) + scaleIn(Motion.enter(Motion.LONG), initialScale = 1.03f)
private val popExit: ExitTransition =
    fadeOut(Motion.exit(Motion.LONG)) + scaleOut(Motion.exit(Motion.LONG), targetScale = 0.94f)

@Composable
fun AppRoot(
    session: SessionManager,
    onAuthenticate: () -> Unit,
    authState: AuthUiState = AuthUiState(),
    onOpenSecuritySettings: () -> Unit = {},
) {
    val locked by session.locked.collectAsState()
    // The vault UI is composed from the first unlock on and then KEPT across locks: a lock no
    // longer destroys the navigation graph (folder path, open note text, scroll positions).
    var everUnlocked by remember { mutableStateOf(!locked) }
    LaunchedEffect(locked) { if (!locked) everUnlocked = true }

    // Unlock reveal: short fade + faint 1.03 -> 1 zoom. Locking is an instant cut (snap to 0).
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(locked) {
        if (locked) reveal.snapTo(0f)
        else reveal.animateTo(1f, tween(Motion.LONG, 0, Motion.EaseOutQuint))
    }

    // Nothing typed may stay focused (keyboard up) behind the lock screen.
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(locked) {
        if (locked) { focusManager.clearFocus(force = true); keyboard?.hide() }
    }

    // While locked, the screens underneath are capped at CREATED: lifecycle-aware work (players,
    // collectors using the lifecycle) stops as if the app were in the background.
    val parentLifecycle = LocalLifecycleOwner.current
    val gated = remember(parentLifecycle) { GatedLifecycleOwner(parentLifecycle) }
    DisposableEffect(gated) {
        gated.attach()
        onDispose { gated.detach() }
    }
    // Applied after composition: lifecycle events must not be dispatched mid-composition.
    androidx.compose.runtime.SideEffect { gated.open = !locked }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (everUnlocked) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Locked: invisible to TalkBack, never drawn (not even a frame), and the
                    // opaque lock screen on top takes every touch.
                    .then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)
                    .graphicsLayer {
                        val p = reveal.value
                        alpha = p
                        val s = 1.03f - 0.03f * p
                        scaleX = s
                        scaleY = s
                    }
                    .drawWithContent { if (!locked) drawContent() },
            ) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides gated,
                    LocalVaultLocked provides locked,
                ) {
                    VaultShell(session = session, locked = locked)
                }
            }
        }
        if (locked) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Swallow every pointer event so nothing reaches the vault underneath.
                    .pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent() }
                    },
            ) {
                AuthScreen(
                    onAuthenticate = onAuthenticate,
                    state = authState,
                    onOpenSecuritySettings = onOpenSecuritySettings,
                )
            }
        }
    }
}

@Composable
private fun VaultShell(session: SessionManager, locked: Boolean) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in tabRoutes
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
        // Reselecting Cartelle while on it brings the vault back to its root.
        if (route == "folders" && nav.currentDestination?.route == "folders") {
            com.cripta.app.ui.vault.VaultTabReselect.notifyReselected()
            return
        }
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // A video must not keep playing behind the lock screen: close the player on lock (its
    // position is saved as usual). Everything else stays exactly where it was.
    LaunchedEffect(locked) {
        if (locked) {
            while (nav.currentDestination?.route?.startsWith("viewer") == true) {
                if (!nav.popBackStack()) break
            }
        }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val entryPoints = androidx.compose.runtime.remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            ctx.applicationContext, com.cripta.app.CriptaApp.AppEntryPoint::class.java,
        )
    }
    // A link shared into the app: jump to the Download tab so the user can pick a resolution.
    // (DownloadScreen reads and clears the pending link.) Waits for the unlock.
    val sharedLinks = remember { entryPoints.sharedLinkStore() }
    val pendingLink by sharedLinks.pending.collectAsState()
    // Tap on the "duplicate scan finished" notification: go to Settings, which opens the results.
    val dupStore = remember { entryPoints.dupScanStore() }
    val openDup by dupStore.openRequested.collectAsState()
    LaunchedEffect(openDup, locked) {
        if (!locked && openDup && currentRoute != "settings") onTab("settings")
    }
    LaunchedEffect(pendingLink, locked) {
        if (!locked && pendingLink != null && currentRoute != "download") onTab("download")
    }
    // Files shared into the app (possibly while locked): encrypt them into the vault root as soon
    // as it is unlocked, and show Cartelle, whose banner reports progress and the outcome.
    val sharedFiles = remember { entryPoints.sharedFilesStore() }
    val pendingFiles by sharedFiles.pending.collectAsState()
    LaunchedEffect(pendingFiles, locked) {
        if (!locked && pendingFiles.isNotEmpty()) {
            val uris = sharedFiles.take()
            if (uris.isNotEmpty()) {
                runCatching {
                    com.cripta.app.work.ConversionService.startImport(ctx, uris, folderId = null, fromShare = true)
                }.onFailure {
                    android.widget.Toast.makeText(ctx, "Impossibile avviare l'importazione dei file condivisi", android.widget.Toast.LENGTH_LONG).show()
                }
                if (currentRoute != "folders") onTab("folders")
            }
        }
    }

    Scaffold(
        // Each child screen has its own Scaffold that applies the status-bar inset to its
        // TopAppBar. If this outer Scaffold also consumed the top inset into `pad`, the content
        // (and thus the child TopAppBar) would be pushed down by the status bar height twice,
        // making every top bar look too tall / too low. Zero it here; children own the top inset.
        // The bottom NavigationBar still handles its own navigation-bar inset internally.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Portrait: bottom navigation bar. Landscape: a side rail instead (see content), so the
        // short landscape height isn't eaten by a bottom bar. Both slide in/out with the screen
        // instead of snapping (size animates too, so the content never jumps at the end).
        bottomBar = {
            AnimatedVisibility(
                visible = showBar && !landscape,
                enter = slideInVertically(Motion.enter(Motion.MEDIUM)) { it } +
                    expandVertically(Motion.enter(Motion.MEDIUM)) +
                    fadeIn(Motion.enter(Motion.MEDIUM)),
                exit = slideOutVertically(Motion.exit(Motion.MEDIUM)) { it } +
                    shrinkVertically(Motion.exit(Motion.MEDIUM)) +
                    fadeOut(Motion.exit(Motion.MEDIUM)),
            ) {
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
                // (Every non-player route: favourites and notes have top bars under the camera too.)
                if (currentRoute?.startsWith("viewer") != true) Modifier.padding(start = cutoutStart, end = cutoutEnd) else Modifier,
            ),
        ) {
          CompositionLocalProvider(LocalSideCutout provides SideCutout(cutoutStart, cutoutEnd)) {
            AnimatedVisibility(
                visible = showBar && landscape,
                enter = slideInHorizontally(Motion.enter(Motion.MEDIUM)) { -it } +
                    expandHorizontally(Motion.enter(Motion.MEDIUM)) +
                    fadeIn(Motion.enter(Motion.MEDIUM)),
                exit = slideOutHorizontally(Motion.exit(Motion.MEDIUM)) { -it } +
                    shrinkHorizontally(Motion.exit(Motion.MEDIUM)) +
                    fadeOut(Motion.exit(Motion.MEDIUM)),
            ) {
                // Inset the rail from the left edge / status bar so its labels don't touch it.
                NavigationRail(
                    // Top only (not the bottom gesture inset): 4 labelled items must fit a short screen.
                    windowInsets = WindowInsets.systemBars.only(
                        WindowInsetsSides.Start + WindowInsetsSides.Top,
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
                // With the rail shown it already took the start system-bar inset: consume it so the
                // screens' own Scaffolds don't add the same gap again.
                modifier = Modifier.weight(1f).then(
                    if (showBar && landscape) Modifier.consumeWindowInsets(WindowInsets.systemBars.only(WindowInsetsSides.Start))
                    else Modifier,
                ),
                // Tabs are siblings (fade-through); pushed screens move in depth (Z-axis).
                enterTransition = { if (isTabSwitch()) tabEnter else pushEnter },
                exitTransition = { if (isTabSwitch()) tabExit else pushExit },
                popEnterTransition = { if (isTabSwitch()) tabEnter else popEnter },
                popExitTransition = { if (isTabSwitch()) tabExit else popExit },
            ) {
            composable("home") {
                HomeScreen(
                    onOpenFile = { nav.navigate("viewer/$it") },
                    onOpenFolders = {
                        // Switch to the Cartelle tab with the same semantics as the bottom bar,
                        // so the back stack stays consistent and the Home tab keeps working.
                        onTab("folders")
                    },
                    onOpenFavorites = { nav.navigate("favorites") },
                    onLock = { session.lock() },
                    onOpenSettings = { onTab("settings") },
                    onOpenDownload = { onTab("download") },
                )
            }
            composable("folders") {
                VaultScreen(
                    onOpenFile = { nav.navigate("viewer/$it") },
                    onNewNote = { folder -> nav.navigate(if (folder != null) "note/new?folder=$folder" else "note/new") },
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
            composable(
                "note/new?folder={folder}",
                arguments = listOf(androidx.navigation.navArgument("folder") {
                    type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null
                }),
            ) {
                com.cripta.app.ui.note.NoteEditorScreen(fileId = null, onBack = { nav.popBackStack() })
            }
            composable("note/{id}") { entry ->
                com.cripta.app.ui.note.NoteEditorScreen(fileId = entry.arguments?.getString("id"), onBack = { nav.popBackStack() })
            }
            }
          }
        }
    }
}

/**
 * A lifecycle that follows [parent] but is capped at CREATED while the gate is closed (vault
 * locked). Provided to the vault screens so their STARTED/RESUMED-scoped work stops under the
 * lock screen and resumes after unlock. Main thread only.
 */
private class GatedLifecycleOwner(private val parent: LifecycleOwner) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    var open: Boolean = false
        set(value) { if (field != value) { field = value; sync() } }

    private val observer = LifecycleEventObserver { _, _ -> sync() }

    fun attach() {
        parent.lifecycle.addObserver(observer)
        sync()
    }

    fun detach() {
        parent.lifecycle.removeObserver(observer)
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private fun sync() {
        val p = parent.lifecycle.currentState
        val target = if (open) p else minOf(p, Lifecycle.State.CREATED)
        // A registry can't go back from DESTROYED, nor straight from INITIALIZED to DESTROYED.
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        if (target == Lifecycle.State.DESTROYED && !registry.currentState.isAtLeast(Lifecycle.State.CREATED)) return
        if (target == Lifecycle.State.INITIALIZED) return
        registry.currentState = target
    }
}
