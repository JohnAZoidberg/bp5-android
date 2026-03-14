package me.danielschaefer.android.buspirate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val PIN_LABELS =
    (0..7).map { index ->
        when (index) {
            4 -> "IO4 (UART TX)"
            5 -> "IO5 (UART RX)"
            else -> "IO$index"
        }
    }
private val UART_PINS = setOf(4, 5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSettingsDialog(
    currentRstPin: Int,
    currentBootPin: Int,
    onSave: (rstPin: Int, bootPin: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var rstPin by remember { mutableIntStateOf(currentRstPin) }
    var bootPin by remember { mutableIntStateOf(currentBootPin) }
    var rstExpanded by remember { mutableStateOf(false) }
    var bootExpanded by remember { mutableStateOf(false) }

    val pinsConflict = rstPin == bootPin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("EC Pin Settings") },
        text = {
            Column {
                Text("Reset pin", style = MaterialTheme.typography.labelMedium)
                ExposedDropdownMenuBox(
                    expanded = rstExpanded,
                    onExpandedChange = { rstExpanded = it },
                ) {
                    OutlinedTextField(
                        value = PIN_LABELS[rstPin],
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(rstExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = rstExpanded,
                        onDismissRequest = { rstExpanded = false },
                    ) {
                        PIN_LABELS.forEachIndexed { index, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                enabled = index !in UART_PINS,
                                onClick = {
                                    rstPin = index
                                    rstExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Bootloader pin", style = MaterialTheme.typography.labelMedium)
                ExposedDropdownMenuBox(
                    expanded = bootExpanded,
                    onExpandedChange = { bootExpanded = it },
                ) {
                    OutlinedTextField(
                        value = PIN_LABELS[bootPin],
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bootExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = bootExpanded,
                        onDismissRequest = { bootExpanded = false },
                    ) {
                        PIN_LABELS.forEachIndexed { index, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                enabled = index !in UART_PINS,
                                onClick = {
                                    bootPin = index
                                    bootExpanded = false
                                },
                            )
                        }
                    }
                }

                if (pinsConflict) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Reset and bootloader pins must be different.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(rstPin, bootPin) },
                enabled = !pinsConflict,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
