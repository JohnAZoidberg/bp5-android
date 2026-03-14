package me.danielschaefer.android.buspirate.flash

import me.danielschaefer.android.buspirate.protocol.BpioProtocol
import me.danielschaefer.android.buspirate.protocol.BpioResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

sealed class FlashState {
    data object Idle : FlashState()

    data object EnteringFlashMode : FlashState()

    data object Syncing : FlashState()

    data class Detected(val chipName: String, val flashSize: Int) : FlashState()

    data object UploadingMonitor : FlashState()

    data class Flashing(val progress: Float) : FlashState()

    data object Rebooting : FlashState()

    data object Done : FlashState()

    data class Error(val message: String) : FlashState()
}

const val EC_RST_PIN = 7
const val EC_BOOT_PIN = 6

class EcFlasher(
    private val sendAndReceive: suspend (ByteArray) -> BpioResponse,
    private val sendConfig: suspend (ByteArray) -> BpioResponse,
    private val onStateChanged: (FlashState) -> Unit,
    private val rstPin: Int = EC_RST_PIN,
    private val bootPin: Int = EC_BOOT_PIN,
) {
    suspend fun flash(
        monitorData: ByteArray,
        imageData: ByteArray,
    ) {
        enterFlashMode()
        sync()
        val chip = detectChip()
        uploadMonitor(monitorData)
        flashImage(imageData)
        rebootEc()
        onStateChanged(FlashState.Done)
    }

    private suspend fun enterFlashMode() {
        onStateChanged(FlashState.EnteringFlashMode)
        // Set rstPin and bootPin as outputs, both low
        sendConfig(
            BpioProtocol.buildGpioConfigRequest(
                ioDirectionMask = (1 shl rstPin) or (1 shl bootPin),
                ioDirection = (1 shl rstPin) or (1 shl bootPin),
                ioValueMask = (1 shl rstPin) or (1 shl bootPin),
                ioValue = 0,
            ),
        )
        delay(100)
        // Release RST (rstPin -> input), keep FLPRG1 low
        sendConfig(
            BpioProtocol.buildGpioConfigRequest(
                ioDirectionMask = 1 shl rstPin,
                ioDirection = 0,
            ),
        )
        delay(1000)
        // Release FLPRG1 (bootPin -> input)
        sendConfig(
            BpioProtocol.buildGpioConfigRequest(
                ioDirectionMask = 1 shl bootPin,
                ioDirection = 0,
            ),
        )
        delay(100)
    }

    private suspend fun sync() {
        onStateChanged(FlashState.Syncing)
        for (attempt in 0 until 3) {
            val request =
                BpioProtocol.buildDataExchangeRequest(
                    NpcxProtocol.buildSyncPacket(),
                    NpcxProtocol.syncResponseSize(),
                )
            val resp = withTimeout(10_000) { sendAndReceive(request) }
            if (resp is BpioResponse.Data &&
                resp.data.isNotEmpty() &&
                resp.data[0] == NpcxProtocol.SYNC_DEVICE
            ) {
                return
            }
            delay(500)
        }
        throw FlashException("Sync failed: no 0x5A response")
    }

    private suspend fun detectChip(): NpcxProtocol.ChipInfo? {
        val devId = readChunk(NpcxProtocol.DEVICE_ID_CR, 1)[0].toInt() and 0xFF
        val chipId = readChunk(NpcxProtocol.SRID_CR, 1)[0].toInt() and 0xFF
        val chip = NpcxProtocol.lookupChip(devId, chipId)
        if (chip != null) {
            onStateChanged(FlashState.Detected(chip.name, chip.flashSize))
        }
        return chip
    }

    private suspend fun uploadMonitor(monitorData: ByteArray) {
        onStateChanged(FlashState.UploadingMonitor)
        var addr = NpcxProtocol.MONITOR_ADDR
        var offset = 0
        var remaining = monitorData.size
        while (remaining > 0) {
            val chunk = minOf(remaining, NpcxProtocol.MAX_RW_DATA_SIZE)
            writeChunk(addr, monitorData.copyOfRange(offset, offset + chunk))
            addr += chunk
            offset += chunk
            remaining -= chunk
        }
    }

    private suspend fun flashImage(imageData: ByteArray) {
        val fileSize = imageData.size
        var bufOffset = 0
        var flashIndex = 0

        while (bufOffset < fileSize) {
            val segSize = minOf(NpcxProtocol.FIRMWARE_SEGMENT, fileSize - bufOffset)
            val segment = imageData.copyOfRange(bufOffset, bufOffset + segSize)

            if (segment.all { it == 0xFF.toByte() }) {
                // Erase-only: src_addr=0 signals monitor to erase without write
                val hdr =
                    NpcxProtocol.buildMonitorHeader(
                        NpcxProtocol.MONITOR_HDR_TAG,
                        segSize,
                        0L,
                        flashIndex,
                    )
                writeChunk(NpcxProtocol.MONITOR_HDR_ADDR, hdr)
                execReturn(NpcxProtocol.MONITOR_ADDR)
            } else {
                // Write header then upload segment data then execute monitor
                val hdr =
                    NpcxProtocol.buildMonitorHeader(
                        NpcxProtocol.MONITOR_HDR_TAG,
                        segSize,
                        NpcxProtocol.FIRMWARE_START_ADDR,
                        flashIndex,
                    )
                writeChunk(NpcxProtocol.MONITOR_HDR_ADDR, hdr)

                var addr = NpcxProtocol.FIRMWARE_START_ADDR
                var chunkOffset = 0
                while (chunkOffset < segSize) {
                    val chunk = minOf(NpcxProtocol.MAX_RW_DATA_SIZE, segSize - chunkOffset)
                    writeChunk(addr, segment.copyOfRange(chunkOffset, chunkOffset + chunk))
                    addr += chunk
                    chunkOffset += chunk
                }
                execReturn(NpcxProtocol.MONITOR_ADDR)
            }

            bufOffset += segSize
            flashIndex += segSize
            onStateChanged(FlashState.Flashing(bufOffset.toFloat() / fileSize))
        }

        // Clear monitor header tag
        writeChunk(
            NpcxProtocol.MONITOR_HDR_ADDR,
            NpcxProtocol.buildMonitorHeader(0L, 0, 0L, 0),
        )
    }

    private suspend fun rebootEc() {
        onStateChanged(FlashState.Rebooting)
        // Assert RST low
        sendConfig(
            BpioProtocol.buildGpioConfigRequest(
                ioDirectionMask = 1 shl rstPin,
                ioDirection = 1 shl rstPin,
                ioValueMask = 1 shl rstPin,
                ioValue = 0,
            ),
        )
        delay(500)
        // Release RST and FLPRG to input
        sendConfig(
            BpioProtocol.buildGpioConfigRequest(
                ioDirectionMask = (1 shl rstPin) or (1 shl bootPin),
                ioDirection = 0,
            ),
        )
    }

    private suspend fun writeChunk(
        addr: Long,
        data: ByteArray,
    ) {
        val cmd = NpcxProtocol.buildWriteCmd(addr, data)
        val request = BpioProtocol.buildDataExchangeRequest(cmd, NpcxProtocol.writeResponseSize())
        val resp = withTimeout(10_000) { sendAndReceive(request) }
        if (resp is BpioResponse.Data) {
            NpcxProtocol.validateWriteResponse(resp.data)
        } else {
            throw FlashException("Write at 0x${"%08X".format(addr)}: unexpected response $resp")
        }
    }

    private suspend fun readChunk(
        addr: Long,
        size: Int,
    ): ByteArray {
        val cmd = NpcxProtocol.buildReadCmd(addr, size)
        val request = BpioProtocol.buildDataExchangeRequest(cmd, NpcxProtocol.readResponseSize(size))
        val resp = withTimeout(10_000) { sendAndReceive(request) }
        if (resp is BpioResponse.Data) {
            return NpcxProtocol.extractReadData(resp.data, size)
        }
        throw FlashException("Read at 0x${"%08X".format(addr)}: unexpected response $resp")
    }

    private suspend fun execReturn(addr: Long) {
        val cmd = NpcxProtocol.buildExecCmd(addr)
        val request = BpioProtocol.buildDataExchangeRequest(cmd, NpcxProtocol.execResponseSize())
        val resp = withTimeout(10_000) { sendAndReceive(request) }
        if (resp is BpioResponse.Data) {
            NpcxProtocol.validateExecResponse(resp.data)
        } else {
            throw FlashException("Exec at 0x${"%08X".format(addr)}: unexpected response $resp")
        }
    }
}
