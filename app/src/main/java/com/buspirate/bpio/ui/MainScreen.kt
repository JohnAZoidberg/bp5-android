package com.buspirate.bpio.ui

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.buspirate.bpio.flash.FlashState
import com.buspirate.bpio.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val isFlashing =
        state.flashState != FlashState.Idle &&
            state.flashState !is FlashState.Done &&
            state.flashState !is FlashState.Error

    val filePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                val name =
                    uri.let { u ->
                        context.contentResolver.query(u, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) cursor.getString(idx) else null
                            } else {
                                null
                            }
                        }
                    } ?: "ec.bin"
                viewModel.setFirmwareUri(uri, name)
            }
        }

    LaunchedEffect(state.logLines.size) {
        if (state.logLines.isNotEmpty()) {
            listState.animateScrollToItem(state.logLines.size - 1)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .padding(16.dp),
        ) {
            Text(
                text = state.connectionStatus,
                style = MaterialTheme.typography.titleMedium,
            )

            state.deviceStatus?.let { status ->
                Text(
                    text = "FW ${status.firmwareVersion} | HW ${status.hardwareVersion} | ${status.currentMode}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                Button(
                    onClick = {
                        if (state.uartEnabled) viewModel.disableUart() else viewModel.enableUart()
                    },
                    enabled = state.connectionStatus == "Connected" && !isFlashing,
                ) {
                    Text(if (state.uartEnabled) "Disable UART" else "Enable UART")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (state.psuEnabled) viewModel.disablePsu() else viewModel.enablePsu()
                    },
                    enabled = state.connectionStatus == "Connected" && !isFlashing,
                ) {
                    Text(if (state.psuEnabled) "PSU Off" else "PSU On")
                }
            }

            FlashSection(
                flashState = state.flashState,
                selectedFirmwareName = state.selectedFirmwareName,
                isConnected = state.connectionStatus == "Connected",
                onSelectFile = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                onFlash = { viewModel.startFlash(context) },
                onCancel = { viewModel.cancelFlash() },
                onDismiss = { viewModel.resetFlashState() },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Log",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val text = viewModel.getLogText()
                        val intent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                        context.startActivity(Intent.createChooser(intent, "Share log"))
                    },
                    enabled = state.logLines.isNotEmpty(),
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_share),
                        contentDescription = "Share log",
                    )
                }
                IconButton(
                    onClick = { viewModel.clearLog() },
                    enabled = state.logLines.isNotEmpty(),
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_delete),
                        contentDescription = "Clear log",
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                items(state.logLines) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Command...") },
                    singleLine = true,
                    enabled = state.uartEnabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions =
                        KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendCommand(inputText)
                                    inputText = ""
                                }
                            },
                        ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        viewModel.sendCommand(inputText)
                        inputText = ""
                    },
                    enabled = state.uartEnabled && inputText.isNotBlank(),
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_send),
                        contentDescription = "Send",
                    )
                }
            }
        }
    }
}
