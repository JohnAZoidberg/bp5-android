package com.buspirate.bpio.protocol

import bpio.ConfigurationResponse
import bpio.DataResponse
import bpio.RequestPacket
import bpio.RequestPacketContents
import bpio.ResponsePacket
import bpio.ResponsePacketContents
import bpio.StatusRequest
import bpio.StatusResponse
import com.google.flatbuffers.FlatBufferBuilder
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BpioProtocolTest {
    @Test
    fun buildStatusRequestDecodesAsStatusRequest() {
        val encoded = BpioProtocol.buildStatusRequest()
        val decoded = CobsCodec.decode(stripDelimiter(encoded))
        val packet = RequestPacket.getRootAsRequestPacket(ByteBuffer.wrap(decoded))

        assertEquals(RequestPacketContents.StatusRequest, packet.contentsType)
        val sr = packet.contents(StatusRequest()) as StatusRequest
        assertEquals(0, sr.queryLength)
    }

    @Test
    fun buildUartEnableRequestDecodesAsConfigurationRequest() {
        val encoded = BpioProtocol.buildUartEnableRequest()
        val decoded = CobsCodec.decode(stripDelimiter(encoded))
        val packet = RequestPacket.getRootAsRequestPacket(ByteBuffer.wrap(decoded))

        assertEquals(RequestPacketContents.ConfigurationRequest, packet.contentsType)
    }

    @Test
    fun buildUartDisableRequestSetsHiZMode() {
        val encoded = BpioProtocol.buildUartDisableRequest()
        val decoded = CobsCodec.decode(stripDelimiter(encoded))
        val packet = RequestPacket.getRootAsRequestPacket(ByteBuffer.wrap(decoded))

        assertEquals(RequestPacketContents.ConfigurationRequest, packet.contentsType)
    }

    @Test
    fun buildDataWriteRequestContainsData() {
        val testData = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F)
        val encoded = BpioProtocol.buildDataWriteRequest(testData)
        val decoded = CobsCodec.decode(stripDelimiter(encoded))
        val packet = RequestPacket.getRootAsRequestPacket(ByteBuffer.wrap(decoded))

        assertEquals(RequestPacketContents.DataRequest, packet.contentsType)
    }

    @Test
    fun parseStatusResponse() {
        val builder = FlatBufferBuilder(256)
        val modeStr = builder.createString("HiZ")
        val sr =
            StatusResponse.createStatusResponse(
                builder,
                errorOffset = 0,
                versionFlatbuffersMajor = 2u,
                versionFlatbuffersMinor = 2u,
                versionHardwareMajor = 5u,
                versionHardwareMinor = 1u,
                versionFirmwareMajor = 3u,
                versionFirmwareMinor = 7u,
                versionFirmwareGitHashOffset = 0,
                versionFirmwareDateOffset = 0,
                modesAvailableOffset = 0,
                modeCurrentOffset = modeStr,
                modePinLabelsOffset = 0,
                modeBitorderMsb = false,
                modeMaxPacketSize = 0u,
                modeMaxWrite = 0u,
                modeMaxRead = 0u,
                psuEnabled = false,
                psuSetMv = 0u,
                psuSetMa = 0u,
                psuMeasuredMv = 0u,
                psuMeasuredMa = 0u,
                psuCurrentError = false,
                pullupEnabled = false,
                adcMvOffset = 0,
                ioDirection = 0u,
                ioValue = 0u,
                diskSizeMb = 0f,
                diskUsedMb = 0f,
                ledCount = 0u,
            )
        val rp =
            ResponsePacket.createResponsePacket(
                builder,
                errorOffset = 0,
                contentsType = ResponsePacketContents.StatusResponse,
                contentsOffset = sr,
            )
        builder.finish(rp)

        val cobsFrame = CobsCodec.encode(extractBytes(builder))
        val response = BpioProtocol.parseResponse(stripDelimiter(cobsFrame))

        assertTrue(response is BpioResponse.Status)
        val status = (response as BpioResponse.Status).status
        assertEquals("3.7", status.firmwareVersion)
        assertEquals("5.1", status.hardwareVersion)
        assertEquals("HiZ", status.currentMode)
    }

    @Test
    fun parseConfigurationResponse() {
        val builder = FlatBufferBuilder(64)
        val cr = ConfigurationResponse.createConfigurationResponse(builder, errorOffset = 0)
        val rp =
            ResponsePacket.createResponsePacket(
                builder,
                errorOffset = 0,
                contentsType = ResponsePacketContents.ConfigurationResponse,
                contentsOffset = cr,
            )
        builder.finish(rp)

        val cobsFrame = CobsCodec.encode(extractBytes(builder))
        val response = BpioProtocol.parseResponse(stripDelimiter(cobsFrame))

        assertTrue(response is BpioResponse.Configuration)
        assertNull((response as BpioResponse.Configuration).error)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun parseDataResponse() {
        val builder = FlatBufferBuilder(64)
        val dataVec =
            DataResponse.createDataReadVector(
                builder,
                ubyteArrayOf(0x41u, 0x42u, 0x43u),
            )
        val dr =
            DataResponse.createDataResponse(
                builder,
                errorOffset = 0,
                dataReadOffset = dataVec,
                isAsync = true,
            )
        val rp =
            ResponsePacket.createResponsePacket(
                builder,
                errorOffset = 0,
                contentsType = ResponsePacketContents.DataResponse,
                contentsOffset = dr,
            )
        builder.finish(rp)

        val cobsFrame = CobsCodec.encode(extractBytes(builder))
        val response = BpioProtocol.parseResponse(stripDelimiter(cobsFrame))

        assertTrue(response is BpioResponse.Data)
        val dataResponse = response as BpioResponse.Data
        assertArrayEquals(byteArrayOf(0x41, 0x42, 0x43), dataResponse.data)
        assertTrue(dataResponse.isAsync)
    }

    @Test
    fun frameAccumulatorSplitsOnDelimiter() {
        val acc = FrameAccumulator()
        val frame1 = byteArrayOf(0x02, 0x11)
        val frame2 = byteArrayOf(0x03, 0x22, 0x33)
        val combined = frame1 + byteArrayOf(0x00) + frame2 + byteArrayOf(0x00)

        val frames = acc.feed(combined)
        assertEquals(2, frames.size)
        assertArrayEquals(frame1, frames[0])
        assertArrayEquals(frame2, frames[1])
    }

    @Test
    fun frameAccumulatorHandlesPartialFrames() {
        val acc = FrameAccumulator()

        val frames1 = acc.feed(byteArrayOf(0x02, 0x11))
        assertTrue(frames1.isEmpty())

        val frames2 = acc.feed(byteArrayOf(0x00))
        assertEquals(1, frames2.size)
        assertArrayEquals(byteArrayOf(0x02, 0x11), frames2[0])
    }

    private fun stripDelimiter(encoded: ByteArray): ByteArray = encoded.copyOf(encoded.size - 1)

    private fun extractBytes(builder: FlatBufferBuilder): ByteArray {
        val buf = builder.dataBuffer()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        return bytes
    }
}
