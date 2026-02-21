package com.buspirate.bpio.protocol

import bpio.ConfigurationRequest
import bpio.ConfigurationResponse
import bpio.DataRequest
import bpio.DataResponse
import bpio.ModeConfiguration
import bpio.RequestPacket
import bpio.RequestPacketContents
import bpio.ResponsePacket
import bpio.ResponsePacketContents
import bpio.StatusRequest
import bpio.StatusResponse
import com.buspirate.bpio.model.BpStatus
import com.google.flatbuffers.FlatBufferBuilder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

sealed class BpioResponse {
    data class Status(val status: BpStatus) : BpioResponse()

    data class Configuration(val error: String?) : BpioResponse()

    data class Data(
        val data: ByteArray,
        val isAsync: Boolean,
    ) : BpioResponse() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Data) return false
            return data.contentEquals(other.data) && isAsync == other.isAsync
        }

        override fun hashCode(): Int = data.contentHashCode() * 31 + isAsync.hashCode()
    }

    data class Error(val message: String) : BpioResponse()
}

object BpioProtocol {
    private const val VERSION_MAJOR: UByte = 2u
    private const val VERSION_MINOR: UShort = 2u
    private const val NUM_LEDS = 18

    fun buildStatusRequest(): ByteArray {
        val builder = FlatBufferBuilder(64)
        val statusReq = StatusRequest.createStatusRequest(builder, 0)
        val packet =
            RequestPacket.createRequestPacket(
                builder,
                VERSION_MAJOR,
                VERSION_MINOR,
                RequestPacketContents.StatusRequest,
                statusReq,
            )
        builder.finish(packet)
        return CobsCodec.encode(extractBytes(builder))
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun buildUartEnableRequest(
        baudRate: UInt = 115200u,
        dataBits: UByte = 8u,
        parity: Boolean = false,
        stopBits: UByte = 1u,
    ): ByteArray {
        val builder = FlatBufferBuilder(256)
        val modeStr = builder.createString("UART")
        val modeCfg =
            ModeConfiguration.createModeConfiguration(
                builder,
                speed = baudRate,
                dataBits = dataBits,
                parity = parity,
                stopBits = stopBits,
                flowControl = false,
                signalInversion = false,
                clockStretch = false,
                clockPolarity = false,
                clockPhase = false,
                chipSelectIdle = true,
                submode = 0u,
                txModulation = 0u,
                rxSensor = 0u,
            )
        val ledColors = ConfigurationRequest.createLedColorVector(builder, UIntArray(NUM_LEDS) { 0u })
        ConfigurationRequest.startConfigurationRequest(builder)
        ConfigurationRequest.addMode(builder, modeStr)
        ConfigurationRequest.addModeConfiguration(builder, modeCfg)
        ConfigurationRequest.addLedColor(builder, ledColors)
        val cfgReq = ConfigurationRequest.endConfigurationRequest(builder)
        val packet =
            RequestPacket.createRequestPacket(
                builder,
                VERSION_MAJOR,
                VERSION_MINOR,
                RequestPacketContents.ConfigurationRequest,
                cfgReq,
            )
        builder.finish(packet)
        return CobsCodec.encode(extractBytes(builder))
    }

    fun buildUartDisableRequest(): ByteArray {
        val builder = FlatBufferBuilder(128)
        val modeStr = builder.createString("HiZ")
        val modeCfg =
            ModeConfiguration.createModeConfiguration(
                builder,
                speed = 0u,
                dataBits = 0u,
                parity = false,
                stopBits = 0u,
                flowControl = false,
                signalInversion = false,
                clockStretch = false,
                clockPolarity = false,
                clockPhase = false,
                chipSelectIdle = false,
                submode = 0u,
                txModulation = 0u,
                rxSensor = 0u,
            )
        ConfigurationRequest.startConfigurationRequest(builder)
        ConfigurationRequest.addMode(builder, modeStr)
        ConfigurationRequest.addModeConfiguration(builder, modeCfg)
        ConfigurationRequest.addLedResume(builder, true)
        val cfgReq = ConfigurationRequest.endConfigurationRequest(builder)
        val packet =
            RequestPacket.createRequestPacket(
                builder,
                VERSION_MAJOR,
                VERSION_MINOR,
                RequestPacketContents.ConfigurationRequest,
                cfgReq,
            )
        builder.finish(packet)
        return CobsCodec.encode(extractBytes(builder))
    }

    fun buildPsuEnableRequest(
        voltageMv: UInt = 3300u,
        currentMa: UShort = 300u.toUShort(),
    ): ByteArray {
        val builder = FlatBufferBuilder(64)
        ConfigurationRequest.startConfigurationRequest(builder)
        ConfigurationRequest.addPsuEnable(builder, true)
        ConfigurationRequest.addPsuSetMv(builder, voltageMv)
        ConfigurationRequest.addPsuSetMa(builder, currentMa)
        val cfgReq = ConfigurationRequest.endConfigurationRequest(builder)
        val packet =
            RequestPacket.createRequestPacket(
                builder,
                VERSION_MAJOR,
                VERSION_MINOR,
                RequestPacketContents.ConfigurationRequest,
                cfgReq,
            )
        builder.finish(packet)
        return CobsCodec.encode(extractBytes(builder))
    }

    fun buildPsuDisableRequest(): ByteArray {
        val builder = FlatBufferBuilder(64)
        ConfigurationRequest.startConfigurationRequest(builder)
        ConfigurationRequest.addPsuDisable(builder, true)
        val cfgReq = ConfigurationRequest.endConfigurationRequest(builder)
        val packet =
            RequestPacket.createRequestPacket(
                builder,
                VERSION_MAJOR,
                VERSION_MINOR,
                RequestPacketContents.ConfigurationRequest,
                cfgReq,
            )
        builder.finish(packet)
        return CobsCodec.encode(extractBytes(builder))
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun buildDataWriteRequest(data: ByteArray): ByteArray {
        val builder = FlatBufferBuilder(64 + data.size)
        val dataVec = DataRequest.createDataWriteVector(builder, data.toUByteArray())
        val dataReq =
            DataRequest.createDataRequest(
                builder,
                startMain = false,
                startAlt = false,
                dataWriteOffset = dataVec,
                bytesRead = 0u,
                stopMain = false,
                stopAlt = false,
            )
        val packet =
            RequestPacket.createRequestPacket(
                builder,
                VERSION_MAJOR,
                VERSION_MINOR,
                RequestPacketContents.DataRequest,
                dataReq,
            )
        builder.finish(packet)
        return CobsCodec.encode(extractBytes(builder))
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun buildDataExchangeRequest(
        dataWrite: ByteArray,
        bytesRead: Int,
    ): ByteArray {
        val builder = FlatBufferBuilder(64 + dataWrite.size)
        val dataVec = DataRequest.createDataWriteVector(builder, dataWrite.toUByteArray())
        val dataReq =
            DataRequest.createDataRequest(
                builder,
                startMain = false,
                startAlt = false,
                dataWriteOffset = dataVec,
                bytesRead = bytesRead.toUShort(),
                stopMain = false,
                stopAlt = false,
            )
        val packet =
            RequestPacket.createRequestPacket(
                builder,
                VERSION_MAJOR,
                VERSION_MINOR,
                RequestPacketContents.DataRequest,
                dataReq,
            )
        builder.finish(packet)
        return CobsCodec.encode(extractBytes(builder))
    }

    fun buildGpioConfigRequest(
        ioDirectionMask: Int,
        ioDirection: Int,
        ioValueMask: Int = 0,
        ioValue: Int = 0,
    ): ByteArray {
        val builder = FlatBufferBuilder(64)
        ConfigurationRequest.startConfigurationRequest(builder)
        ConfigurationRequest.addIoDirectionMask(builder, ioDirectionMask.toUByte())
        ConfigurationRequest.addIoDirection(builder, ioDirection.toUByte())
        ConfigurationRequest.addIoValueMask(builder, ioValueMask.toUByte())
        ConfigurationRequest.addIoValue(builder, ioValue.toUByte())
        val cfgReq = ConfigurationRequest.endConfigurationRequest(builder)
        val packet =
            RequestPacket.createRequestPacket(
                builder,
                VERSION_MAJOR,
                VERSION_MINOR,
                RequestPacketContents.ConfigurationRequest,
                cfgReq,
            )
        builder.finish(packet)
        return CobsCodec.encode(extractBytes(builder))
    }

    fun parseResponse(cobsFrame: ByteArray): BpioResponse {
        val decoded = CobsCodec.decode(cobsFrame)
        val buf = ByteBuffer.wrap(decoded)
        val responsePacket = ResponsePacket.getRootAsResponsePacket(buf)

        val packetError = responsePacket.error
        if (packetError != null) {
            return BpioResponse.Error(packetError)
        }

        return when (responsePacket.contentsType) {
            ResponsePacketContents.StatusResponse -> {
                val sr = responsePacket.contents(StatusResponse()) as StatusResponse
                BpioResponse.Status(
                    BpStatus(
                        firmwareVersion = "${sr.versionFirmwareMajor}.${sr.versionFirmwareMinor}",
                        hardwareVersion = "${sr.versionHardwareMajor}.${sr.versionHardwareMinor}",
                        currentMode = sr.modeCurrent ?: "Unknown",
                        psuEnabled = sr.psuEnabled,
                    ),
                )
            }
            ResponsePacketContents.ConfigurationResponse -> {
                val cr = responsePacket.contents(ConfigurationResponse()) as ConfigurationResponse
                BpioResponse.Configuration(cr.error)
            }
            ResponsePacketContents.DataResponse -> {
                val dr = responsePacket.contents(DataResponse()) as DataResponse
                val drError = dr.error
                if (drError != null) {
                    return BpioResponse.Error(drError)
                }
                val readData = ByteArray(dr.dataReadLength) { dr.dataRead(it).toByte() }
                BpioResponse.Data(readData, dr.isAsync)
            }
            else -> BpioResponse.Error("Unknown response type: ${responsePacket.contentsType}")
        }
    }

    private fun extractBytes(builder: FlatBufferBuilder): ByteArray {
        val buf = builder.dataBuffer()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        return bytes
    }
}

class FrameAccumulator {
    private val buffer = ByteArrayOutputStream()

    fun feed(data: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        for (b in data) {
            if (b == 0.toByte() && buffer.size() > 0) {
                val frame = buffer.toByteArray()
                buffer.reset()
                frames.add(frame)
            } else if (b != 0.toByte()) {
                buffer.write(b.toInt())
            }
        }
        return frames
    }
}
