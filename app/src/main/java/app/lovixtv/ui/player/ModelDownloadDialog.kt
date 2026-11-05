/*
 * Model download screen for Vosk speech recognition models.
 * Shows progress when downloading models on first app launch.
 */
package app.lovixtv.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.lovixtv.player.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ModelDownloadDialog(
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Downloading English model...") }
    var downloading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloader = remember { ModelDownloader(context) }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            // Download English model first
            val success = downloader.downloadModel("en") { p ->
                progress = p
                status = "Downloading English model... ${(p * 100).toInt()}%"
            }
            
            if (success) {
                downloading = false
                status = "Model download complete!"
                kotlinx.coroutines.delay(1000)
                onComplete()
            } else {
                downloading = false
                status = "Download failed. Please try again."
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Setting up subtitles",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                if (downloading) {
                    CircularProgressIndicator(progress = progress)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(status)
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(status)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onComplete) {
                        onDismiss()
                    }) {
                        Text("Continue")
                    }
                }
            }
        }
    }
}
