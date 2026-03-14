package me.danielschaefer.android.buspirate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.danielschaefer.android.buspirate.flash.FlashState

@Composable
fun FlashSection(
    flashState: FlashState,
    selectedFirmwareName: String?,
    isConnected: Boolean,
    onSelectFile: () -> Unit,
    onFlash: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))

        when (flashState) {
            is FlashState.Idle -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onSelectFile) {
                        Text("Select ec.bin")
                    }
                    if (selectedFirmwareName != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedFirmwareName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onFlash,
                        enabled = isConnected && selectedFirmwareName != null,
                    ) {
                        Text("Flash EC")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onReset,
                        enabled = isConnected,
                    ) {
                        Text("Reset EC")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onSettings) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_manage),
                            contentDescription = "Pin settings",
                        )
                    }
                }
            }
            is FlashState.EnteringFlashMode,
            is FlashState.Syncing,
            is FlashState.Detected,
            is FlashState.UploadingMonitor,
            is FlashState.Flashing,
            is FlashState.Rebooting,
            -> {
                val stepText =
                    when (flashState) {
                        is FlashState.EnteringFlashMode -> "Entering flash mode..."
                        is FlashState.Syncing -> "Syncing with EC..."
                        is FlashState.Detected -> "Detected ${flashState.chipName}"
                        is FlashState.UploadingMonitor -> "Uploading monitor..."
                        is FlashState.Flashing -> "Flashing: ${(flashState.progress * 100).toInt()}%"
                        is FlashState.Rebooting -> "Rebooting EC..."
                        else -> ""
                    }
                Text(text = stepText, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                if (flashState is FlashState.Flashing) {
                    LinearProgressIndicator(
                        progress = { flashState.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onCancel,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text("Cancel")
                }
            }
            is FlashState.Done -> {
                Text(
                    text = "Flash complete!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
            is FlashState.Error -> {
                Text(
                    text = "Flash error: ${flashState.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
