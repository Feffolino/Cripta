package com.cripta.app.ui.download

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.VaultRepository.DownloadPhase
import com.cripta.app.work.ConversionService

/**
 * In-app video downloader: paste a link (YouTube, Vimeo, HLS, direct...), pick a quality, and the
 * video is fetched with yt-dlp, encrypted into the vault and its source link stored on the file.
 * Live progress is shown both here and in the notification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(initialUrl: String? = null, vm: DownloadViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(initialUrl.orEmpty()) }
    // null = Auto (source quality); otherwise a max output height in px.
    var quality by remember { mutableStateOf<Int?>(null) }
    val qualities = listOf<Pair<Int?, String>>(null to "Auto", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")
    val state by vm.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { com.cripta.app.ui.components.HeaderTitle("Download") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Incolla il link di un video (YouTube, Vimeo, HLS, link diretto e molti altri). Viene " +
                    "scaricato con yt-dlp, cifrato nel vault e il link resta nelle informazioni del file. " +
                    "Il primo download prepara il motore e può richiedere qualche secondo. I contenuti " +
                    "protetti da DRM non sono scaricabili.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link video") },
                singleLine = true,
                enabled = !state.active,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Qualità", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                qualities.forEach { (h, label) ->
                    FilterChip(
                        selected = quality == h,
                        onClick = { quality = h },
                        enabled = !state.active,
                        label = { Text(label) },
                    )
                }
            }
            Button(
                onClick = {
                    val u = url.trim()
                    if (u.isNotBlank()) {
                        vm.dismissResult()
                        ConversionService.startDownloadUrl(ctx, u, quality)
                        url = ""
                    }
                },
                enabled = url.isNotBlank() && !state.active,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Scarica") }

            DownloadStatus(state, onCancel = { vm.cancel(ctx) }, onDismiss = { vm.dismissResult() })
        }
    }
}

@Composable
private fun DownloadStatus(
    state: com.cripta.app.data.VaultRepository.DownloadState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state.phase) {
        DownloadPhase.PREPARING -> {
            Text("Preparazione del motore…", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Annulla") }
        }
        DownloadPhase.DOWNLOADING -> {
            val eta = if (state.etaSec > 0) " · resta ${etaText(state.etaSec)}" else ""
            Text("Download in corso: ${state.pct}%$eta", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { state.pct / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Annulla") }
        }
        DownloadPhase.DONE -> {
            Text(
                "Download completato: ${state.message.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("OK") }
        }
        DownloadPhase.FAILED -> {
            Text(
                "Download fallito: ${state.message.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("OK") }
        }
        DownloadPhase.CANCELLED -> {
            Text("Download annullato", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("OK") }
        }
        DownloadPhase.IDLE -> {}
    }
}

private fun etaText(sec: Long): String =
    if (sec >= 60) "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}" else "${sec}s"
