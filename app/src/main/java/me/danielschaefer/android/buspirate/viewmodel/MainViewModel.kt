package me.danielschaefer.android.buspirate.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.danielschaefer.android.buspirate.R
import me.danielschaefer.android.buspirate.flash.EC_BOOT_PIN
import me.danielschaefer.android.buspirate.flash.EC_RST_PIN
import me.danielschaefer.android.buspirate.flash.EcFlasher
import me.danielschaefer.android.buspirate.flash.FlashState
import me.danielschaefer.android.buspirate.model.BpStatus
import me.danielschaefer.android.buspirate.protocol.BpioProtocol
import me.danielschaefer.android.buspirate.protocol.BpioResponse
import me.danielschaefer.android.buspirate.protocol.FrameAccumulator
import me.danielschaefer.android.buspirate.usb.UsbSerialManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val connectionStatus: String = "Disconnected",
    val deviceStatus: BpStatus? = null,
    val uartEnabled: Boolean = false,
    val psuEnabled: Boolean = false,
    val logLines: List<String> = emptyList(),
    val errorMessage: String? = null,
    val flashState: FlashState = FlashState.Idle,
    val selectedFirmwareUri: Uri? = null,
    val selectedFirmwareName: String? = null,
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var usbManager: UsbSerialManager? = null
    private var readJob: Job? = null
    private var flashJob: Job? = null
    private val accumulator = FrameAccumulator()
    private var responseChannel: Channel<BpioResponse>? = null

    fun setUsbManager(manager: UsbSerialManager) {
        usbManager = manager
    }

    fun connect() {
        val manager = usbManager ?: return
        _uiState.update { it.copy(connectionStatus = "Connecting...") }

        manager.connect { result ->
            result.onSuccess {
                _uiState.update { it.copy(connectionStatus = "Connected") }
                sendStatusRequest()
                startReadLoop()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        connectionStatus = "Disconnected",
                        errorMessage = e.message,
                    )
                }
            }
        }
    }

    fun enableUart() {
        val manager = usbManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            safeWrite(manager, BpioProtocol.buildUartEnableRequest())
        }
    }

    fun disableUart() {
        val manager = usbManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            safeWrite(manager, BpioProtocol.buildUartDisableRequest())
        }
    }

    fun enablePsu() {
        val manager = usbManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            safeWrite(manager, BpioProtocol.buildPsuEnableRequest())
        }
    }

    fun disablePsu() {
        val manager = usbManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            safeWrite(manager, BpioProtocol.buildPsuDisableRequest())
        }
    }

    fun sendCommand(text: String) {
        if (text.isBlank()) return
        val manager = usbManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val data = (text + "\r\n").toByteArray(Charsets.UTF_8)
            safeWrite(manager, BpioProtocol.buildDataWriteRequest(data))
        }
    }

    fun clearLog() {
        _uiState.update { it.copy(logLines = emptyList()) }
    }

    fun getLogText(): String = _uiState.value.logLines.joinToString("\n")

    fun retryIfPending() {
        if (_uiState.value.connectionStatus != "Connected") {
            connect()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setFirmwareUri(
        uri: Uri,
        name: String,
    ) {
        _uiState.update { it.copy(selectedFirmwareUri = uri, selectedFirmwareName = name) }
    }

    fun startFlash(context: Context) {
        val manager = usbManager ?: return
        val uri = _uiState.value.selectedFirmwareUri ?: return

        flashJob?.cancel()
        flashJob =
            viewModelScope.launch(Dispatchers.IO) {
                val channel = Channel<BpioResponse>(Channel.RENDEZVOUS)
                responseChannel = channel

                try {
                    if (!_uiState.value.psuEnabled) {
                        safeWrite(manager, BpioProtocol.buildPsuEnableRequest())
                        channel.receive()
                    }
                    if (!_uiState.value.uartEnabled) {
                        safeWrite(manager, BpioProtocol.buildUartEnableRequest())
                        channel.receive()
                    }

                    val imageData =
                        context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes()
                        } ?: throw Exception("Cannot read firmware file")

                    val monitorData =
                        context.resources.openRawResource(R.raw.npcx_monitor).use {
                            it.readBytes()
                        }

                    val flasher =
                        EcFlasher(
                            sendAndReceive = { request ->
                                safeWrite(manager, request)
                                channel.receive()
                            },
                            sendConfig = { request ->
                                safeWrite(manager, request)
                                channel.receive()
                            },
                            onStateChanged = { state ->
                                _uiState.update { it.copy(flashState = state) }
                            },
                        )

                    flasher.flash(monitorData, imageData)
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(flashState = FlashState.Error(e.message ?: "Flash failed"))
                    }
                } finally {
                    responseChannel = null
                    channel.close()
                }
            }
    }

    fun cancelFlash() {
        flashJob?.cancel()
        flashJob = null
        responseChannel?.close()
        responseChannel = null
        _uiState.update { it.copy(flashState = FlashState.Idle) }
    }

    fun resetEc() {
        val manager = usbManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Assert RST low
            safeWrite(
                manager,
                BpioProtocol.buildGpioConfigRequest(
                    ioDirectionMask = 1 shl EC_RST_PIN,
                    ioDirection = 1 shl EC_RST_PIN,
                    ioValueMask = 1 shl EC_RST_PIN,
                    ioValue = 0,
                ),
            )
            delay(500)
            // Release RST and FLPRG to input
            safeWrite(
                manager,
                BpioProtocol.buildGpioConfigRequest(
                    ioDirectionMask = (1 shl EC_RST_PIN) or (1 shl EC_BOOT_PIN),
                    ioDirection = 0,
                ),
            )
        }
    }

    fun resetFlashState() {
        _uiState.update { it.copy(flashState = FlashState.Idle) }
    }

    private fun sendStatusRequest() {
        val manager = usbManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            safeWrite(manager, BpioProtocol.buildStatusRequest())
        }
    }

    private fun safeWrite(
        manager: UsbSerialManager,
        data: ByteArray,
    ) {
        try {
            manager.write(data)
        } catch (_: Exception) {
            handleDisconnect()
        }
    }

    private fun handleDisconnect() {
        readJob?.cancel()
        readJob = null
        usbManager?.disconnect()
        cancelFlash()
        _uiState.update {
            UiState(
                connectionStatus = "Disconnected",
                errorMessage = "Device disconnected",
            )
        }
    }

    private fun startReadLoop() {
        readJob?.cancel()
        readJob =
            viewModelScope.launch(Dispatchers.IO) {
                val buffer = ByteArray(4096)
                try {
                    while (isActive) {
                        val manager = usbManager ?: break
                        val bytesRead = manager.read(buffer)
                        if (bytesRead > 0) {
                            val chunk = buffer.copyOf(bytesRead)
                            val frames = accumulator.feed(chunk)
                            for (frame in frames) {
                                handleFrame(frame)
                            }
                        }
                    }
                } catch (_: Exception) {
                    handleDisconnect()
                }
            }
    }

    private fun handleFrame(frame: ByteArray) {
        try {
            when (val response = BpioProtocol.parseResponse(frame)) {
                is BpioResponse.Status -> {
                    _uiState.update {
                        it.copy(
                            deviceStatus = response.status,
                            uartEnabled = response.status.currentMode == "UART",
                            psuEnabled = response.status.psuEnabled,
                        )
                    }
                }
                is BpioResponse.Configuration -> {
                    val ch = responseChannel
                    if (ch != null) {
                        ch.trySend(response)
                    } else {
                        if (response.error != null) {
                            _uiState.update { it.copy(errorMessage = response.error) }
                        }
                        sendStatusRequest()
                    }
                }
                is BpioResponse.Data -> {
                    val ch = responseChannel
                    if (ch != null && !response.isAsync) {
                        ch.trySend(response)
                    } else if (response.data.isNotEmpty()) {
                        val text = String(response.data, Charsets.UTF_8)
                        appendData(text)
                    }
                }
                is BpioResponse.Error -> {
                    _uiState.update { it.copy(errorMessage = response.message) }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Parse error: ${e.message}") }
        }
    }

    private fun appendData(text: String) {
        _uiState.update { state ->
            val lines = state.logLines.toMutableList()
            val pending = if (lines.isNotEmpty()) lines.removeAt(lines.size - 1) else ""
            val cleaned = stripAnsiSequences(text)
            val combined = pending + cleaned.replace("\r\n", "\n").replace("\r", "\n")
            val parts = combined.split("\n")
            // All parts except the last are complete lines; the last is the pending partial
            lines.addAll(parts)
            if (lines.size > MAX_LOG_LINES) {
                state.copy(logLines = lines.takeLast(MAX_LOG_LINES))
            } else {
                state.copy(logLines = lines)
            }
        }
    }

    private fun stripAnsiSequences(text: String): String {
        // Remove ANSI CSI escape sequences: ESC [ params command
        // This covers CSI sequences (cursor movement, clearing, colors, etc.)
        return text.replace(Regex("\u001b\\[[0-9;]*[A-Za-z]"), "")
    }

    override fun onCleared() {
        super.onCleared()
        flashJob?.cancel()
        readJob?.cancel()
        usbManager?.disconnect()
    }

    companion object {
        private const val MAX_LOG_LINES = 1000
    }
}
