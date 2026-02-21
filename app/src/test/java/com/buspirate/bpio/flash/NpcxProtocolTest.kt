package com.buspirate.bpio.flash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NpcxProtocolTest {
    @Test
    fun crc16EmptyData() {
        assertEquals(0x0000, NpcxProtocol.crc16(byteArrayOf()))
    }

    @Test
    fun crc16SingleByte() {
        // CRC-16 of 0x55 (sync byte) with polynomial 0xA001
        val crc = NpcxProtocol.crc16(byteArrayOf(0x55))
        assertEquals(0x3FC0, crc)
    }

    @Test
    fun crc16KnownVector() {
        // "123456789" is a standard CRC test vector
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        val crc = NpcxProtocol.crc16(data)
        // CRC-16/ARC (poly 0xA001, init 0x0000) of "123456789" = 0xBB3D
        assertEquals(0xBB3D, crc)
    }

    @Test
    fun syncPacketIsSingleByte() {
        val pkt = NpcxProtocol.buildSyncPacket()
        assertEquals(1, pkt.size)
        assertEquals(0x55.toByte(), pkt[0])
    }

    @Test
    fun writeCmdStructure() {
        val data = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val pkt = NpcxProtocol.buildWriteCmd(0x200C3000L, data)

        // CMD + size-1 + 4 addr bytes + data + 2 CRC = 2 + 4 + 4 + 2 = 12
        assertEquals(12, pkt.size)
        assertEquals(NpcxProtocol.CMD_WRITE, pkt[0])
        assertEquals(3.toByte(), pkt[1]) // data.size - 1
        // Address big-endian: 0x200C3000
        assertEquals(0x20.toByte(), pkt[2])
        assertEquals(0x0C.toByte(), pkt[3])
        assertEquals(0x30.toByte(), pkt[4])
        assertEquals(0x00.toByte(), pkt[5])
        // Data
        assertEquals(0x11.toByte(), pkt[6])
        assertEquals(0x22.toByte(), pkt[7])
        assertEquals(0x33.toByte(), pkt[8])
        assertEquals(0x44.toByte(), pkt[9])
        // CRC covers bytes 0..9
        val expectedCrc = NpcxProtocol.crc16(pkt.copyOfRange(0, 10))
        assertEquals((expectedCrc ushr 8).toByte(), pkt[10])
        assertEquals(expectedCrc.toByte(), pkt[11])
    }

    @Test
    fun readCmdStructure() {
        val pkt = NpcxProtocol.buildReadCmd(0x400C1022L, 1)

        assertEquals(8, pkt.size)
        assertEquals(NpcxProtocol.CMD_READ, pkt[0])
        assertEquals(0.toByte(), pkt[1]) // size - 1
        // Address big-endian: 0x400C1022
        assertEquals(0x40.toByte(), pkt[2])
        assertEquals(0x0C.toByte(), pkt[3])
        assertEquals(0x10.toByte(), pkt[4])
        assertEquals(0x22.toByte(), pkt[5])
        val expectedCrc = NpcxProtocol.crc16(pkt.copyOfRange(0, 6))
        assertEquals((expectedCrc ushr 8).toByte(), pkt[6])
        assertEquals(expectedCrc.toByte(), pkt[7])
    }

    @Test
    fun execCmdStructure() {
        val pkt = NpcxProtocol.buildExecCmd(0x200C3020L)

        assertEquals(8, pkt.size)
        assertEquals(NpcxProtocol.CMD_FCALL, pkt[0])
        assertEquals(0x00.toByte(), pkt[1])
        assertEquals(0x20.toByte(), pkt[2])
        assertEquals(0x0C.toByte(), pkt[3])
        assertEquals(0x30.toByte(), pkt[4])
        assertEquals(0x20.toByte(), pkt[5])
        val expectedCrc = NpcxProtocol.crc16(pkt.copyOfRange(0, 6))
        assertEquals((expectedCrc ushr 8).toByte(), pkt[6])
        assertEquals(expectedCrc.toByte(), pkt[7])
    }

    @Test
    fun monitorHeaderIsLittleEndian() {
        val hdr =
            NpcxProtocol.buildMonitorHeader(
                tag = 0xA5075001L,
                size = 0x1000,
                srcAddr = 0x10090000L,
                flashIndex = 0,
            )
        assertEquals(16, hdr.size)
        // Tag: 0xA5075001 little-endian
        assertEquals(0x01.toByte(), hdr[0])
        assertEquals(0x50.toByte(), hdr[1])
        assertEquals(0x07.toByte(), hdr[2])
        assertEquals(0xA5.toByte(), hdr[3])
        // Size: 0x1000 little-endian
        assertEquals(0x00.toByte(), hdr[4])
        assertEquals(0x10.toByte(), hdr[5])
        assertEquals(0x00.toByte(), hdr[6])
        assertEquals(0x00.toByte(), hdr[7])
        // srcAddr: 0x10090000 little-endian
        assertEquals(0x00.toByte(), hdr[8])
        assertEquals(0x00.toByte(), hdr[9])
        assertEquals(0x09.toByte(), hdr[10])
        assertEquals(0x10.toByte(), hdr[11])
        // flashIndex: 0 little-endian
        assertEquals(0x00.toByte(), hdr[12])
        assertEquals(0x00.toByte(), hdr[13])
        assertEquals(0x00.toByte(), hdr[14])
        assertEquals(0x00.toByte(), hdr[15])
    }

    @Test
    fun validateWriteResponseAcceptsValid() {
        NpcxProtocol.validateWriteResponse(byteArrayOf(NpcxProtocol.CMD_WRITE))
    }

    @Test(expected = FlashException::class)
    fun validateWriteResponseRejectsEmpty() {
        NpcxProtocol.validateWriteResponse(byteArrayOf())
    }

    @Test(expected = FlashException::class)
    fun validateWriteResponseRejectsWrongByte() {
        NpcxProtocol.validateWriteResponse(byteArrayOf(0x00))
    }

    @Test
    fun extractReadDataReturnsPayload() {
        val resp = byteArrayOf(NpcxProtocol.CMD_READ, 0x42, 0x00, 0x00)
        val data = NpcxProtocol.extractReadData(resp, 1)
        assertArrayEquals(byteArrayOf(0x42), data)
    }

    @Test(expected = FlashException::class)
    fun extractReadDataRejectsWrongCmd() {
        NpcxProtocol.extractReadData(byteArrayOf(0x00, 0x42, 0x00, 0x00), 1)
    }

    @Test
    fun validateExecResponseAcceptsValid() {
        NpcxProtocol.validateExecResponse(
            byteArrayOf(0x00, NpcxProtocol.CMD_FCALL_RESULT, 0x03),
        )
    }

    @Test(expected = FlashException::class)
    fun validateExecResponseRejectsBadResult() {
        NpcxProtocol.validateExecResponse(
            byteArrayOf(0x00, NpcxProtocol.CMD_FCALL_RESULT, 0x01),
        )
    }

    @Test(expected = FlashException::class)
    fun validateExecResponseRejectsTooShort() {
        NpcxProtocol.validateExecResponse(byteArrayOf(0x00))
    }

    @Test
    fun chipLookupKnownChip() {
        val chip = NpcxProtocol.lookupChip(0x21, 0x06)
        assertNotNull(chip)
        assertEquals("NPCX796FA", chip!!.name)
        assertEquals(1024 * 1024, chip.flashSize)
    }

    @Test
    fun chipLookupNpcx996f() {
        val chip = NpcxProtocol.lookupChip(0x21, 0x09)
        assertNotNull(chip)
        assertEquals("NPCX996F", chip!!.name)
        assertEquals(512 * 1024, chip.flashSize)
    }

    @Test
    fun chipLookupUnknownReturnsNull() {
        assertNull(NpcxProtocol.lookupChip(0xFF, 0xFF))
    }

    @Test
    fun responseSizes() {
        assertEquals(1, NpcxProtocol.syncResponseSize())
        assertEquals(1, NpcxProtocol.writeResponseSize())
        assertEquals(7, NpcxProtocol.readResponseSize(4)) // 4 + 3
        assertEquals(3, NpcxProtocol.execResponseSize())
    }
}
