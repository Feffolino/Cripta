package com.cripta.app.ui

import com.cripta.app.R
import com.cripta.app.str

import kotlinx.coroutines.launch
import com.cripta.app.ui.theme.CriptaIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Icon
import com.cripta.app.ui.theme.accentTint
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

// DS: outline icon at rest, filled for the current tab.
private data class Tab(val route: String, val label: String, val icon: ImageVector, val iconRest: ImageVector)

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

private val tabs get() = listOf(
    Tab("home", str(R.string.tab_home), CriptaIcons.HomeFilled, CriptaIcons.Home),
    Tab("folders", str(R.string.tab_folders), CriptaIcons.FolderFilled, CriptaIcons.Folder),
    Tab("download", str(R.string.tab_download), CriptaIcons.DownloadFilled, CriptaIcons.Download),
    Tab("settings", str(R.string.tab_settings), CriptaIcons.SettingsFilled, CriptaIcons.Settings),
)
private val tabRoutes = tabs.map { it.route }.toSet()

/** Tab ↔ tab: siblings, so a fade-through (no depth). Anything else is a push/pop in depth. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

/** +1 when the new tab sits right of the old one in the bar, -1 when left. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int {
    val from = tabs.indexOfFirst { it.route == initialState.destination.route }
    val to = tabs.indexOfFirst { it.route == targetState.destination.route }
    return if (to >= from) 1 else -1
}

/** The player (and its media) opens in depth; every other page slides along the reading direction. */
private fun NavBackStackEntry.isViewer() = destination.route?.startsWith("viewer") == true

// Between tabs: a short drift towards the tab chosen in the bar while the pages cross-fade, so
// the switch says "next to it", not "on top of it".
private fun tabEnter(dir: Int): EnterTransition =
    fadeIn(tween(Motion.MEDIUM, Motion.SHORT / 2, Motion.EaseOutQuart)) + slideInHorizontally(Motion.enter(Motion.LONG)) { w -> dir * w / 14 }
private fun tabExit(dir: Int): ExitTransition =
    fadeOut(Motion.exit(Motion.SHORT)) + slideOutHorizontally(Motion.exit(Motion.MEDIUM)) { w -> -dir * w / 20 }

// Z-axis for the player: it comes forward from slightly behind, the page recedes towards the
// viewer. Pop is the exact reverse.
private val pushEnter: EnterTransition =
    fadeIn(Motion.enter(Motion.LONG)) + scaleIn(Motion.enter(Motion.LONG), initialScale = 0.94f)
private val pushExit: ExitTransition =
    fadeOut(Motion.exit(Motion.LONG)) + scaleOut(Motion.exit(Motion.LONG), targetScale = 1.03f)
private val popEnter: EnterTransition =
    fadeIn(Motion.enter(Motion.LONG)) + scaleIn(Motion.enter(Motion.LONG), initialScale = 1.03f)
private val popExit: ExitTransition =
    fadeOut(Motion.exit(Motion.LONG)) + scaleOut(Motion.exit(Motion.LONG), targetScale = 0.94f)

// Shared X axis for pages (people, a profile, favourites, a note): the new one slides in from
// the right over a fading page that moves a little left; Back reverses it.
private val pageEnter: EnterTransition =
    fadeIn(tween(Motion.MEDIUM, Motion.SHORT / 2, Motion.EaseOutQuart)) + slideInHorizontally(Motion.enter(Motion.LONG)) { w -> w / 8 }
private val pageExit: ExitTransition =
    fadeOut(tween(Motion.MEDIUM, 0, Motion.EaseOutQuart)) + slideOutHorizontally(Motion.exit(Motion.LONG)) { w -> -w / 12 }
private val pagePopEnter: EnterTransition =
    fadeIn(tween(Motion.MEDIUM, Motion.SHORT / 2, Motion.EaseOutQuart)) + slideInHorizontally(Motion.enter(Motion.LONG)) { w -> -w / 12 }
private val pagePopExit: ExitTransition =
    fadeOut(tween(Motion.MEDIUM, 0, Motion.EaseOutQuart)) + slideOutHorizontally(Motion.exit(Motion.LONG)) { w -> w / 8 }

@Composable
fun AppRoot(
    session: SessionManager,
    onAuthenticate: () -> Unit,
    authState: AuthUiState = AuthUiState(),
    onOpenSecuritySettings: () -> Unit = {},
    onSubmitPin: (CharArray) -> Unit = {},
) {
    val locked by session.locked.collectAsStateWithLifecycle()
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
                    onSubmitPin = onSubmitPin,
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
        // Reselecting Impostazioni inside a category page goes back to the list of pages.
        if (route == "settings" && nav.currentDestination?.route == "settings") {
            com.cripta.app.ui.settings.SettingsTabReselect.notifyReselected()
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
    // A note opens straight in its editor (no read-only page first); any other file in the player.
    val vaultRepo = remember { entryPoints.vaultRepository() }
    val openScope = androidx.compose.runtime.rememberCoroutineScope()
    val openFile: (androidx.navigation.NavBackStackEntry, String) -> Unit = { from, id ->
        openScope.launch {
            val note = vaultRepo.fileById(id)?.let { com.cripta.app.data.VaultRepository.isNote(it.mimeType) } == true
            if (note) nav.openNote(from, id) else nav.openViewer(from, id)
        }
    }
    // A link shared into the app: jump to the Download tab so the user can pick a resolution.
    // (DownloadScreen reads and clears the pending link.) Waits for the unlock.
    val sharedLinks = remember { entryPoints.sharedLinkStore() }
    val pendingLink by sharedLinks.pending.collectAsStateWithLifecycle()
    // Tap on the "duplicate scan finished" notification: go to Settings, which opens the results.
    val dupStore = remember { entryPoints.dupScanStore() }
    val openDup by dupStore.openRequested.collectAsStateWithLifecycle()
    LaunchedEffect(openDup, locked) {
        if (!locked && openDup && currentRoute != "settings") onTab("settings")
    }
    LaunchedEffect(pendingLink, locked) {
        if (!locked && pendingLink != null && currentRoute != "download") onTab("download")
    }
    // Files shared into the app (possibly while locked): encrypt them into the vault root as soon
    // as it is unlocked, and show Cartelle, whose banner reports progress and the outcome.
    val sharedFiles = remember { entryPoints.sharedFilesStore() }
    val pendingFiles by sharedFiles.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingFiles, locked) {
        if (!locked && pendingFiles.isNotEmpty()) {
            val uris = sharedFiles.take()
            if (uris.isNotEmpty()) {
                runCatching {
                    com.cripta.app.work.ConversionService.startImport(ctx, uris, folderId = null, fromShare = true)
                }.onFailure {
                    android.widget.Toast.makeText(ctx, str(R.string.share_import_failed), android.widget.Toast.LENGTH_LONG).show()
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
                // Flat bar one tier above the page, the current tab marked by an accent pill + accent
                // label (shape and colour together, never colour alone).
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp, modifier = Modifier) {
                    tabs.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onTab(tab.route) },
                            icon = { Icon(if (selected) tab.icon else tab.iconRest, tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.SemiBold) },
                            // DS cr-nav: accent-tint pill and filled icon on the current tab, label in ink.
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.accentTint,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        // Content fills edge-to-edge (incl. the display cutout) so the system-bar / cutout strips
        // show whatever screen is behind them — the app surface on the tabs, black in the player.
        // consumeWindowInsets: the bottom bar already took the navigation-bar inset, so the screens'
        // own Scaffolds must not add it again (it left an empty band above the bar).
        Row(
            Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize().then(
                // Keep the tab screens (rail + content) clear of the side camera cutout; the
                // fullscreen player handles its own insets.
                // (Every non-player route: favourites and notes have top bars under the camera too.)
                // With the side rail shown, the rail itself covers the start cutout (in its colour):
                // padded here, that strip stayed the page's dark blue next to the rail.
                if (currentRoute?.startsWith("viewer") != true)
                    Modifier.padding(start = if (showBar && landscape) 0.dp else cutoutStart, end = cutoutEnd) else Modifier,
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    // Top only (not the bottom gesture inset): 4 labelled items must fit a short screen.
                    // The camera cutout too, so the rail's colour fills the strip beside the camera.
                    windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
                        WindowInsetsSides.Start + WindowInsetsSides.Top,
                    ),
                ) {
                    // Items centred vertically (equal flexible space above and below).
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    tabs.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onTab(tab.route) },
                            icon = { Icon(if (selected) tab.icon else tab.iconRest, tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.SemiBold) },
                            // DS cr-nav: accent-tint pill and filled icon on the current tab, label in ink.
                            colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.accentTint,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
            // "Persone" asked for from Settings, Home or the scan's notification. Kept until the
            // unlock: a tap on the notification while locked opened the page under the lock
            // screen, whose first read of the database threw "Vault locked".
            val peopleAsked by com.cripta.app.ui.people.PeopleNav.open.collectAsStateWithLifecycle()
            LaunchedEffect(peopleAsked, locked) {
                if (peopleAsked && !locked) { com.cripta.app.ui.people.PeopleNav.consume(); nav.navigate("people") { launchSingleTop = true } }
            }
            val personAsked by com.cripta.app.ui.people.PeopleNav.person.collectAsStateWithLifecycle()
            LaunchedEffect(personAsked, locked) {
                if (!locked) personAsked?.let { id -> com.cripta.app.ui.people.PeopleNav.consumePerson(); nav.navigate("person/$id") }
            }
            NavHost(
                navController = nav,
                startDestination = "home",
                // With the rail shown it already took the start system-bar inset: consume it so the
                // screens' own Scaffolds don't add the same gap again.
                modifier = Modifier.weight(1f).then(
                    if (showBar && landscape) Modifier.consumeWindowInsets(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Start))
                    else Modifier,
                ),
                // Tabs are siblings (fade-through with a drift); the player moves in depth (Z-axis);
                // other pages slide along X.
                enterTransition = { when { isTabSwitch() -> tabEnter(tabDirection()); targetState.isViewer() -> pushEnter; else -> pageEnter } },
                exitTransition = { when { isTabSwitch() -> tabExit(tabDirection()); targetState.isViewer() -> pushExit; else -> pageExit } },
                popEnterTransition = { when { isTabSwitch() -> tabEnter(tabDirection()); initialState.isViewer() -> popEnter; else -> pagePopEnter } },
                popExitTransition = { when { isTabSwitch() -> tabExit(tabDirection()); initialState.isViewer() -> popExit; else -> pagePopExit } },
            ) {
            composable("home") { entry ->
                HomeScreen(
                    onOpenFile = { openFile(entry, it) },
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
            composable("folders") { entry ->
                VaultScreen(
                    onOpenFile = { openFile(entry, it) },
                    onNewNote = { folder -> nav.navigate(if (folder != null) "note/new?folder=$folder" else "note/new") },
                )
            }
            composable("favorites") { entry ->
                FavoritesScreen(
                    onOpenFile = { openFile(entry, it) },
                    onOpenFolders = { onTab("folders") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("download") { entry ->
                com.cripta.app.ui.download.DownloadScreen(onOpenFile = { openFile(entry, it) })
            }
            composable("settings") { entry ->
                SettingsScreen(onBack = { nav.popBackStack() }, onOpenFile = { openFile(entry, it) })
            }
            composable("people") { entry ->
                com.cripta.app.ui.settings.PeopleScreen(onBack = { nav.popBackStack() }, onOpenFile = { openFile(entry, it) })
            }
            composable("person/{id}") { entry ->
                com.cripta.app.ui.people.PersonProfileScreen(
                    personId = entry.arguments?.getString("id")?.toLongOrNull() ?: -1L,
                    onBack = { nav.popBackStack() },
                    onOpenFile = { openFile(entry, it) },
                    onShowInVault = { onTab("folders") },
                )
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

/**
 * Open [fileId] in the player from the page [from], only while that page is the one in front: a
 * second tap, or a tap while the page was still coming back from the player, opened a second player
 * on top of the first (two to close, one under the other).
 */
private fun androidx.navigation.NavController.openViewer(from: androidx.navigation.NavBackStackEntry, fileId: String) {
    if (from.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) navigate("viewer/$fileId")
}

/** Open the note [fileId] in its editor from the page [from], under the same rule as [openViewer]. */
private fun androidx.navigation.NavController.openNote(from: androidx.navigation.NavBackStackEntry, fileId: String) {
    if (from.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) navigate("note/$fileId")
}
