package com.cripta.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.ui.theme.CoverShape

const val MIME_NOTE = "text/cripta-note"

private val sizeUnits = arrayOf("B", "KB", "MB", "GB", "TB")

/** Human-readable byte size, e.g. "3.1 MB". */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < sizeUnits.size - 1) { value /= 1024.0; unit++ }
    val pattern = if (unit == 0) "%.0f %s" else "%.1f %s"
    return String.format(java.util.Locale.getDefault(), pattern, value, sizeUnits[unit])
}

/** Media duration as "m:ss" or "h:mm:ss"; null when unknown/not applicable. */
fun formatDuration(ms: Long?): String? {
    if (ms == null || ms <= 0) return null
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.getDefault(), "%d:%02d", m, s)
}

/** Caption line for a media item: "1:23 · 3.1 MB" for timed media, otherwise just the size. */
fun fileMeta(file: FileEntity): String {
    val size = formatBytes(file.sizeBytes)
    val dur = formatDuration(file.durationMs)
    return if (dur != null) "$dur · $size" else size
}

fun typeIconFor(mime: String): ImageVector = when {
    VaultRepository.isImage(mime) -> Icons.Filled.Image
    VaultRepository.isVideo(mime) -> Icons.Filled.Movie
    mime == MIME_NOTE -> Icons.Filled.Description
    mime == "application/pdf" -> Icons.Filled.PictureAsPdf
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/**
 * Rounded cover with type/favorite/selection marks. Reusable across screens. The cover is
 * decorative (no content description): the cell around it carries the file name as text.
 */
@Composable
fun MediaThumb(
    file: FileEntity,
    thumb: suspend (FileEntity) -> Bitmap?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    coverVersion: Int = 0,
    selectionMode: Boolean = false,
    /** false where the screen draws its own star (Preferiti's remove button). */
    showFavoriteBadge: Boolean = true,
    shape: Shape = CoverShape,
) {
    val bmp by produceState<Bitmap?>(initialValue = null, file.id, coverVersion) { value = thumb(file) }
    val icon = typeIconFor(file.mimeType)
    val isVideo = VaultRepository.isVideo(file.mimeType)
    SelectableThumbFrame(
        selected = selected,
        shape = shape,
        modifier = modifier,
        overlay = { CornerSelectionCheck(selected, selectionMode, size = 22.dp) },
    ) {
        CoverPlaceholderBackground()
        ThumbCrossfade(bmp, contentDescription = null, modifier = Modifier.fillMaxSize()) {
            CoverPlaceholder(icon, 28.dp)
        }
        if (isVideo) CoverPlayMark(Modifier.align(Alignment.Center))
        if (file.isFavorite && showFavoriteBadge) CoverStar(Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}

/** Neutral tile behind a cover while it loads (and around images with transparency). */
@Composable
fun CoverPlaceholderBackground() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

/** A cover without an image: the file type's icon on the neutral tile. */
@Composable
fun CoverPlaceholder(icon: ImageVector, size: Dp) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size))
    }
}

/** Short text on a cover (a video's length): white on the dark glass, round. */
@Composable
fun CoverBadge(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(OnImageGlass).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}

/** The favourite star on a cover, on the dark glass so it reads on light pictures too. */
@Composable
fun CoverStar(modifier: Modifier = Modifier, size: Dp = 22.dp, description: String? = "Preferito") {
    Box(modifier.size(size).clip(CircleShape).background(OnImageGlass), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Star, description, tint = com.cripta.app.ui.theme.Favorite, modifier = Modifier.size(size * 0.68f))
    }
}

/** "This is a video" mark in the middle of a cover, when no length badge says it already. */
@Composable
fun CoverPlayMark(modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(modifier.size(size).clip(CircleShape).background(OnImageGlass), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, "Video", tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

/** Preset folder tint colors (ARGB) offered in the customization dialog. */
val FOLDER_COLORS: List<Int> = listOf(
    0xFF4F86C6, 0xFF63B76C, 0xFFE0A030, 0xFFD9645B,
    0xFF9B6BCC, 0xFF3FB0B3, 0xFFC65B9B, 0xFF7A8794,
).map { it.toInt() }

/** Folder icon tinted by [color], or an [emoji] shown in its place when set. */
@Composable
fun FolderGlyph(color: Int?, emoji: String?, size: Dp) {
    val c = color?.let { Color(it) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (c != null) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(c.copy(alpha = 0.22f)))
        }
        if (!emoji.isNullOrBlank()) {
            // Sized in dp (not scaled by the user's font size) so the emoji always fits its box.
            val emojiSize = with(LocalDensity.current) { (size * 0.6f).toSp() }
            Text(emoji, fontSize = emojiSize, lineHeight = emojiSize, maxLines = 1, softWrap = false)
        } else {
            Icon(Icons.Filled.Folder, null, tint = c ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.68f))
        }
    }
}

/** App logo + title, for the top bar of the main screens. Kept identical across every screen so
 *  the logo is the same size everywhere and the bar stays compact. */
@Composable
fun HeaderTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(com.cripta.app.R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 6.dp))
    }
}

/** Square thumbnail + caption used in shelves and favorite grids. */
@Composable
fun MediaThumbCell(
    file: FileEntity,
    thumb: suspend (FileEntity) -> Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverVersion: Int = 0,
    showFavoriteBadge: Boolean = true,
) {
    Column(modifier.clip(CoverShape).clickable(onClickLabel = "Apri", onClick = onClick).padding(bottom = 6.dp)) {
        MediaThumb(file, thumb, Modifier.fillMaxWidth().aspectRatio(1f), coverVersion = coverVersion,
            showFavoriteBadge = showFavoriteBadge)
        Text(file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        Text(fileMeta(file), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp))
    }
}
