package com.cripta.app.ui.download

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.VaultRepository.DownloadPhase
import com.cripta.app.ui.components.formatBytes
import com.cripta.app.work.ConversionService
import kotlinx.coroutines.delay

/**
 * In-app video downloader: paste (or share in) a link, pick a quality — with a size estimate per
 * resolution — and the video is fetched with yt-dlp, encrypted into the vault and its source link
 * stored on the file. Live progress is shown both here and in the notification.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadScreen(initialUrl: String? = null, vm: DownloadViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var url by remember { mutableStateOf(initialUrl.orEmpty()) }
    var quality by remember { mutableStateOf<Int?>(null) }
    val qualities = listOf<Pair<Int?, String>>(null to "Auto", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")
    val heights = qualities.map { it.first }
    val state by vm.state.collectAsState()
    val estimate by vm.estimate.collectAsState()
    val pending by vm.pendingSharedLink.collectAsState()

    // A link shared from another app: pre-fill the field so the user can pick a resolution.
    LaunchedEffect(pending) {
        pending?.let { url = it; vm.consumeSharedLink() }
    }
    // Debounced size probe whenever the link settles.
    LaunchedEffect(url) {
        val u = url.trim()
        if (u.isBlank()) { vm.resetEstimate() } else { delay(700); vm.estimate(u, heights) }
    }

    Scaffold(topBar = { TopAppBar(title = { com.cripta.app.ui.components.HeaderTitle("Download") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Scarica un video", style = MaterialTheme.typography.titleMedium)
                    Text("YouTube, Vimeo, HLS, link diretti e altri. Cifrato nel vault.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link video") },
                singleLine = true,
                enabled = !state.active,
                modifier = Modifier.fillMaxWidth(),
            )
            // Paste the clipboard link in one tap, or clear the field to start over.
            if (!state.active) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val clip = clipboard.getText()?.text?.trim().orEmpty()
                            if (clip.isNotBlank()) url = clip
                            else Toast.makeText(ctx, "Appunti vuoti", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                        Text("  Incolla")
                    }
                    OutlinedButton(
                        onClick = { url = ""; vm.resetEstimate() },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(18.dp))
                        Text("  Cancella")
                    }
                }
            }

            Text("Qualità", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualities.forEach { (h, label) ->
                    FilterChip(
                        selected = quality == h,
                        onClick = { quality = h },
                        enabled = !state.active,
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(label)
                                SizeHint(estimate, h)
                            }
                        },
                    )
                }
            }
            when (val e = estimate) {
                is DownloadViewModel.Estimate.Error ->
                    Text("Stima non disponibile: ${e.message}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                DownloadViewModel.Estimate.Loading ->
                    Text("Calcolo dimensioni…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {}
            }

            Button(
                onClick = {
                    val u = url.trim()
                    if (u.isNotBlank()) {
                        vm.dismissResult()
                        ConversionService.startDownloadUrl(ctx, u, quality)
                        url = ""; vm.resetEstimate()
                    }
                },
                enabled = url.isNotBlank() && !state.active,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                Text("  Scarica")
            }

            DownloadStatus(state, onCancel = { vm.cancel(ctx) }, onDismiss = { vm.dismissResult() })

            Text(
                "I contenuti protetti da DRM non sono scaricabili. Il primo download prepara il motore " +
                    "e può richiedere qualche secondo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small size estimate shown inside a quality chip. */
@Composable
private fun SizeHint(estimate: DownloadViewModel.Estimate, height: Int?) {
    when (estimate) {
        DownloadViewModel.Estimate.Loading ->
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
        is DownloadViewModel.Estimate.Ready -> {
            val bytes = estimate.bytesByHeight[height]
            Text(
                if (bytes != null) "~${formatBytes(bytes)}" else "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {}
    }
}

@Composable
private fun DownloadStatus(
    state: com.cripta.app.data.VaultRepository.DownloadState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state.phase) {
        DownloadPhase.PREPARING -> ProgressCard("Preparazione del motore…", null, onCancel)
        DownloadPhase.DOWNLOADING -> {
            val eta = if (state.etaSec > 0) " · resta ${etaText(state.etaSec)}" else ""
            ProgressCard("Download: ${state.pct}%$eta", state.pct / 100f, onCancel)
        }
        DownloadPhase.DONE -> ResultCard(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary,
            "Download completato", state.message, onDismiss)
        DownloadPhase.FAILED -> ResultCard(Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error,
            "Download fallito", state.message, onDismiss)
        DownloadPhase.CANCELLED -> ResultCard(Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.onSurfaceVariant,
            "Download annullato", null, onDismiss)
        DownloadPhase.IDLE -> {}
    }
}

@Composable
private fun ProgressCard(label: String, progress: Float?, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Annulla") }
        }
    }
}

@Composable
private fun ResultCard(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color,
                       title: String, message: String?, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
            if (!message.isNullOrBlank()) {
                Text(message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("OK") }
        }
    }
}

private fun etaText(sec: Long): String =
    if (sec >= 60) "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}" else "${sec}s"
