package com.cripta.app.ui.download

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.cripta.app.work.ConversionService

/**
 * In-app video downloader: paste a direct link or an HLS (.m3u8) URL, pick a quality, and the
 * video is downloaded, transcoded to MP4 and encrypted into the vault (progress in the notification).
 * The source link is stored on the file and shown in its Info dialog.
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun DownloadScreen(initialUrl: String? = null) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(initialUrl.orEmpty()) }
    // null = Auto (source quality); otherwise a max output height in px.
    var quality by remember { mutableStateOf<Int?>(null) }
    val qualities = listOf<Pair<Int?, String>>(null to "Auto", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")

    Scaffold(topBar = { TopAppBar(title = { com.cripta.app.ui.components.HeaderTitle("Download") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Scarica un video da un link diretto o HLS (.m3u8). Viene cifrato nel vault e il link " +
                    "resta nelle informazioni del file. I contenuti protetti da DRM non sono scaricabili.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link video") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Qualità", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                qualities.forEach { (h, label) ->
                    FilterChip(selected = quality == h, onClick = { quality = h }, label = { Text(label) })
                }
            }
            Button(
                onClick = {
                    val u = url.trim()
                    if (u.isNotBlank()) {
                        ConversionService.startDownloadUrl(ctx, u, quality)
                        Toast.makeText(ctx, "Download avviato", Toast.LENGTH_SHORT).show()
                        url = ""
                    }
                },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Scarica") }
        }
    }
}
