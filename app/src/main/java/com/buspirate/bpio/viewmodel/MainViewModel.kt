package com.buspirate.bpio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buspirate.bpio.model.BpStatus
import com.buspirate.bpio.protocol.BpioProtocol
import com.buspirate.bpio.protocol.BpioResponse
import com.buspirate.bpio.protocol.FrameAccumulator
import com.buspirate.bpio.usb.UsbSerialManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val logLines: List<String> = emptyList(),
    val errorMessage: String? = null,
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var usbManager: UsbSerialManager? = null
    private var readJob: Job? = null
    private val accumulator = FrameAccumulator()

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

    fun sendCommand(text: String) {
        if (text.isBlank()) return
        val manager = usbManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val data = (text + "\r\n").toByteArray(Charsets.UTF_8)
            safeWrite(manager, BpioProtocol.buildDataWriteRequest(data))
        }
    }

    fun retryIfPending() {
        if (_uiState.value.connectionStatus != "Connected") {
            connect()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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
                        )
                    }
                }
                is BpioResponse.Configuration -> {
                    if (response.error != null) {
                        _uiState.update { it.copy(errorMessage = response.error) }
                    }
                    sendStatusRequest()
                }
                is BpioResponse.Data -> {
                    if (response.data.isNotEmpty()) {
                        val text = String(response.data, Charsets.UTF_8)
                        appendLog(text.trimEnd('\r', '\n'))
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

    private fun appendLog(line: String) {
        _uiState.update { state ->
            val newLines = state.logLines + line
            state.copy(logLines = if (newLines.size > MAX_LOG_LINES) newLines.takeLast(MAX_LOG_LINES) else newLines)
        }
    }

    override fun onCleared() {
        super.onCleared()
        readJob?.cancel()
        usbManager?.disconnect()
    }

    companion object {
        private const val MAX_LOG_LINES = 1000
    }
}
