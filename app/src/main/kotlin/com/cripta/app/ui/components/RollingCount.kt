package com.cripta.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.cripta.app.ui.theme.Motion

/**
 * A line that carries a count ("3 selezionate"): when [count] changes the line rolls up (count
 * grew) or down (it shrank), like the vault's selection title, instead of jumping.
 */
@Composable
fun RollingCountText(count: Int, modifier: Modifier = Modifier, text: (Int) -> String) {
    AnimatedContent(
        targetState = count,
        modifier = modifier,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(Motion.enter()) { h -> if (up) h else -h } + fadeIn(Motion.enter())) togetherWith
                (slideOutVertically(Motion.exit()) { h -> if (up) -h else h } + fadeOut(Motion.exit())) using
                SizeTransform(clip = true)
        },
        label = "rollingCount",
    ) { n -> Text(text(n), maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
