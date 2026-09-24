@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cripta.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The app's one bottom sheet. Every panel that slides up (file details, folder menu, filters,
 * pickers, trash details…) uses it, so they all behave the same:
 *  - portrait: opens in two steps (half, then full on a swipe up); a swipe down from full closes it
 *    in one go instead of stopping at half;
 *  - landscape (short screen): opens fully at once; content panels ([wideInLandscape]: file
 *    details, filters, stats) use the screen width, menus and lists stay 640dp wide and centred
 *    (full-width rows put the label far from the middle);
 *  - fully open, it stops below the status bar / camera instead of running under it;
 *  - Back closes it in one press (with its animation) and returns to the screen below;
 *  - an action inside it slides it away first ([LocalCriptaSheet] + [CriptaSheetState.closeThen]);
 *  - while the vault is locked it is not shown at all (it lives in its own window, above the lock
 *    screen): it comes back after unlocking.
 *
 * New sheets should use this instead of a bare [ModalBottomSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class CriptaSheetState internal constructor(
    internal val sheet: SheetState,
    private val scope: CoroutineScope,
    private val dismiss: () -> Unit,
) {
    /** Top edge of the sheet in its (full-screen) window, px; null before it is laid out. */
    fun topPx(): Float? = runCatching { sheet.requireOffset() }.getOrNull()

    /** Closes with the slide-down animation, then reports the dismissal. */
    fun close() {
        scope.launch { sheet.hide() }.invokeOnCompletion { if (!sheet.isVisible) dismiss() }
    }

    /** Slides the sheet away, then dismisses it and runs [action] (open a dialog, apply a pick…). */
    fun closeThen(action: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion { if (!sheet.isVisible) { dismiss(); action() } }
    }
}

/** The sheet a composable is in (null outside one): `LocalCriptaSheet.current?.closeThen { … }`. */
val LocalCriptaSheet = androidx.compose.runtime.staticCompositionLocalOf<CriptaSheetState?> { null }

/** Runs [action] after sliding the enclosing [CriptaSheet] away, or at once outside a sheet. */
@Composable
fun rememberSheetAction(): (() -> Unit) -> Unit {
    val sheet = LocalCriptaSheet.current
    return remember(sheet) { { action -> if (sheet != null) sheet.closeThen(action) else action() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberCriptaSheetState(onDismiss: () -> Unit): CriptaSheetState {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)
    // Remembered: the sheet state is re-created whenever this callback changes identity.
    val ref = remember { arrayOfNulls<SheetState>(1) }
    val confirm: (SheetValue) -> Boolean = remember {
        { v ->
            val st = ref[0]
            if (st != null && v == SheetValue.PartiallyExpanded && st.currentValue == SheetValue.Expanded) {
                // Swiped down from full: straight to closed, not to half.
                scope.launch { st.hide() }.invokeOnCompletion { if (!st.isVisible) latestDismiss() }
                false
            } else true
        }
    }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = landscape, confirmValueChange = confirm)
    ref[0] = sheet
    return remember(sheet) { CriptaSheetState(sheet, scope) { latestDismiss() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CriptaSheet(
    onDismissRequest: () -> Unit,
    state: CriptaSheetState = rememberCriptaSheetState(onDismissRequest),
    wideInLandscape: Boolean = false,
    /** Dimming behind the sheet; transparent when the screen behind must stay in view. */
    scrimColor: androidx.compose.ui.graphics.Color = BottomSheetDefaults.ScrimColor,
    /** Caps the sheet at this share of the screen height (null: up to the camera area). */
    maxHeightFraction: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Locked vault: nothing of it may show over the lock screen (the sheet has its own window,
    // which the vault's hidden content does not cover). It is shown again after unlocking.
    if (com.cripta.app.ui.LocalVaultLocked.current) return
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Back closes the sheet and returns to the screen below. The sheet's own window does not always
    // get the back event (immersive viewer), and then the screen underneath handled it instead.
    BackHandler(onBack = state::close)
    // Fully open, the sheet stops below the status bar / camera area.
    val topGap = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding(),
        WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
    ) + 8.dp
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = state.sheet,
        scrimColor = scrimColor,
        // Material's own Back first collapses a full sheet to half and needs a second press: off,
        // Back is handled below (in the sheet's window) and closes it in one go.
        properties = androidx.compose.material3.ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        // Landscape content panels: the full width (as in Impostazioni and Scarica).
        sheetMaxWidth = if (landscape && wideInLandscape) 1400.dp else BottomSheetDefaults.SheetMaxWidth,
    ) {
        BoxWithConstraints {
            val cap = maxHeightFraction?.let { maxHeight * it } ?: maxHeight
            Column(
                Modifier.heightIn(max = minOf(cap, maxHeight - topGap).coerceAtLeast(0.dp))
                    .then(
                        if (landscape) Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        else Modifier
                    ),
            ) {
                // Inside the sheet's own window (its dialog dispatcher), where Back arrives while it
                // has focus; the handler above covers the case where the screen's window gets it.
                BackHandler(onBack = state::close)
                androidx.compose.runtime.CompositionLocalProvider(LocalCriptaSheet provides state) { content() }
            }
        }
    }
}
