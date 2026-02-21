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

    fun buildUartEnableRequest(
        baudRate: UInt = 115200u,
        dataBits: UByte = 8u,
        parity: Boolean = false,
        stopBits: UByte = 1u,
    ): ByteArray {
        val builder = FlatBufferBuilder(128)
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
        val cfgReq =
            ConfigurationRequest.createConfigurationRequest(
                builder,
                modeOffset = modeStr,
                modeConfigurationOffset = modeCfg,
                modeBitorderMsb = false,
                modeBitorderLsb = false,
                psuDisable = false,
                psuEnable = false,
                psuSetMv = 0u,
                psuSetMa = 300u,
                pullupDisable = false,
                pullupEnable = false,
                ioDirectionMask = 0u,
                ioDirection = 0u,
                ioValueMask = 0u,
                ioValue = 0u,
                ledResume = false,
                ledColorOffset = 0,
                printStringOffset = 0,
                hardwareBootloader = false,
                hardwareReset = false,
                hardwareSelftest = false,
            )
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
        val builder = FlatBufferBuilder(64)
        val modeStr = builder.createString("HiZ")
        ConfigurationRequest.startConfigurationRequest(builder)
        ConfigurationRequest.addMode(builder, modeStr)
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
