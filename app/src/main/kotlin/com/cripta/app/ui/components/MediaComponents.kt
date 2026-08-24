package com.cripta.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity

const val MIME_NOTE = "text/cripta-note"

fun typeIconFor(mime: String): ImageVector = when {
    VaultRepository.isImage(mime) -> Icons.Filled.Image
    VaultRepository.isVideo(mime) -> Icons.Filled.Movie
    mime == MIME_NOTE -> Icons.Filled.Description
    mime == "application/pdf" -> Icons.Filled.PictureAsPdf
    else -> Icons.Filled.InsertDriveFile
}

/** Rounded thumbnail with type/favorite/selection overlays. Reusable across screens. */
@Composable
fun MediaThumb(
    file: FileEntity,
    thumb: suspend (FileEntity) -> Bitmap?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val bmp by produceState<Bitmap?>(initialValue = null, file.id) { value = thumb(file) }
    val icon = typeIconFor(file.mimeType)
    val isVideo = VaultRepository.isVideo(file.mimeType)
    val borderMod = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier
    Box(modifier.clip(MaterialTheme.shapes.medium).then(borderMod), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
        Crossfade(targetState = bmp, label = "thumb") { b ->
            if (b != null) Image(b.asImageBitmap(), file.originalName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
            }
        }
        if (isVideo) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayCircle, "Video", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        if (file.isFavorite) {
            Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Star, "Preferito", tint = Color(0xFFFFC531), modifier = Modifier.size(16.dp))
            }
        }
        if (selected) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CheckCircle, "Selezionato", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Square thumbnail + caption used in shelves and favorite grids. */
@Composable
fun MediaThumbCell(
    file: FileEntity,
    thumb: suspend (FileEntity) -> Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(onClick = onClick)) {
        MediaThumb(file, thumb, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
    }
}
