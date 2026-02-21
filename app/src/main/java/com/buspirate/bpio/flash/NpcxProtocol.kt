package com.buspirate.bpio.flash

import java.nio.ByteBuffer
import java.nio.ByteOrder

object NpcxProtocol {
    // UUT protocol command bytes
    const val SYNC_HOST = 0x55.toByte()
    const val SYNC_DEVICE = 0x5A.toByte()
    const val CMD_WRITE = 0x07.toByte()
    const val CMD_READ = 0x1C.toByte()
    const val CMD_FCALL = 0x70.toByte()
    const val CMD_FCALL_RESULT = 0x73.toByte()

    // Memory addresses
    const val MONITOR_HDR_ADDR = 0x200C3000L
    const val MONITOR_ADDR = 0x200C3020L
    const val FIRMWARE_START_ADDR = 0x10090000L
    const val MONITOR_HDR_TAG = 0xA5075001L

    // Device ID registers
    const val DEVICE_ID_CR = 0x400C1022L
    const val SRID_CR = 0x400C101CL

    // Sizes
    const val FIRMWARE_SEGMENT = 0x1000 // 4KB
    const val MAX_RW_DATA_SIZE = 256

    // CRC-16 table (polynomial 0xA001, init 0x0000)
    private val CRC16_TABLE =
        intArrayOf(
            0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
            0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
            0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00, 0xCFC1, 0xCE81, 0x0E40,
            0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841,
            0xD801, 0x18C0, 0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40,
            0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80, 0xDC41,
            0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641,
            0xD201, 0x12C0, 0x1380, 0xD341, 0x1100, 0xD1C1, 0xD081, 0x1040,
            0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240,
            0x3600, 0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441,
            0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0, 0x3E80, 0xFE41,
            0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840,
            0x2800, 0xE8C1, 0xE981, 0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41,
            0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
            0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640,
            0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101, 0x21C0, 0x2080, 0xE041,
            0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240,
            0x6600, 0xA6C1, 0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441,
            0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80, 0xAE41,
            0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840,
            0x7800, 0xB8C1, 0xB981, 0x7940, 0xBB01, 0x7BC0, 0x7A80, 0xBA41,
            0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40,
            0xB401, 0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640,
            0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0, 0x7080, 0xB041,
            0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241,
            0x9601, 0x56C0, 0x5780, 0x9741, 0x5500, 0x95C1, 0x9481, 0x5440,
            0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
            0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841,
            0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00, 0x8BC1, 0x8A81, 0x4A40,
            0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41,
            0x4400, 0x84C1, 0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641,
            0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081, 0x4040,
        )

    fun crc16(
        data: ByteArray,
        initial: Int = 0x0000,
    ): Int {
        var crc = initial
        for (b in data) {
            val tmp = crc xor (0xFF and b.toInt())
            crc = (crc ushr 8) xor CRC16_TABLE[tmp and 0xFF]
        }
        return crc and 0xFFFF
    }

    fun buildSyncPacket(): ByteArray = byteArrayOf(SYNC_HOST)

    fun buildWriteCmd(
        addr: Long,
        data: ByteArray,
    ): ByteArray {
        val body = ByteArray(2 + 4 + data.size)
        body[0] = CMD_WRITE
        body[1] = (data.size - 1).toByte()
        body[2] = (addr ushr 24).toByte()
        body[3] = (addr ushr 16).toByte()
        body[4] = (addr ushr 8).toByte()
        body[5] = addr.toByte()
        System.arraycopy(data, 0, body, 6, data.size)
        val crc = crc16(body)
        return body + byteArrayOf((crc ushr 8).toByte(), crc.toByte())
    }

    fun buildReadCmd(
        addr: Long,
        size: Int,
    ): ByteArray {
        val body = ByteArray(2 + 4)
        body[0] = CMD_READ
        body[1] = (size - 1).toByte()
        body[2] = (addr ushr 24).toByte()
        body[3] = (addr ushr 16).toByte()
        body[4] = (addr ushr 8).toByte()
        body[5] = addr.toByte()
        val crc = crc16(body)
        return body + byteArrayOf((crc ushr 8).toByte(), crc.toByte())
    }

    fun buildExecCmd(addr: Long): ByteArray {
        val body = ByteArray(2 + 4)
        body[0] = CMD_FCALL
        body[1] = 0x00
        body[2] = (addr ushr 24).toByte()
        body[3] = (addr ushr 16).toByte()
        body[4] = (addr ushr 8).toByte()
        body[5] = addr.toByte()
        val crc = crc16(body)
        return body + byteArrayOf((crc ushr 8).toByte(), crc.toByte())
    }

    fun buildMonitorHeader(
        tag: Long,
        size: Int,
        srcAddr: Long,
        flashIndex: Int,
    ): ByteArray {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(tag.toInt())
        buf.putInt(size)
        buf.putInt(srcAddr.toInt())
        buf.putInt(flashIndex)
        return buf.array()
    }

    // Write response: single byte matching CMD_WRITE
    fun validateWriteResponse(resp: ByteArray) {
        if (resp.isEmpty() || resp[0] != CMD_WRITE) {
            throw FlashException(
                "Write failed: resp=${if (resp.isEmpty()) "empty" else resp.joinToString("") { "%02X".format(it) }}",
            )
        }
    }

    // Read response: CMD_READ byte + data + 2 CRC bytes
    fun extractReadData(
        resp: ByteArray,
        expectedSize: Int,
    ): ByteArray {
        if (resp.isEmpty() || resp[0] != CMD_READ) {
            throw FlashException(
                "Read failed: resp=${if (resp.isEmpty()) "empty" else resp.joinToString("") { "%02X".format(it) }}",
            )
        }
        return resp.copyOfRange(1, 1 + expectedSize)
    }

    // Exec response: 3 bytes - status, CMD_FCALL_RESULT, result code
    fun validateExecResponse(resp: ByteArray) {
        if (resp.size < 3) {
            throw FlashException(
                "Exec failed: resp=${if (resp.isEmpty()) "empty" else resp.joinToString("") { "%02X".format(it) }}",
            )
        }
        if (resp[1] != CMD_FCALL_RESULT || resp[2] != 0x03.toByte()) {
            throw FlashException(
                "Monitor error: status=0x${"%02X".format(resp[0])} " +
                    "cmd=0x${"%02X".format(resp[1])} result=0x${"%02X".format(resp[2])}",
            )
        }
    }

    data class ChipInfo(val name: String, val flashSize: Int)

    private val CHIP_TABLE =
        listOf(
            Triple(0x21, 0x06, ChipInfo("NPCX796FA", 1024 * 1024)),
            Triple(0x21, 0x07, ChipInfo("NPCX796FB", 1024 * 1024)),
            Triple(0x29, 0x07, ChipInfo("NPCX796FC", 512 * 1024)),
            Triple(0x20, 0x07, ChipInfo("NPCX797FC", 512 * 1024)),
            Triple(0x24, 0x07, ChipInfo("NPCX797WB", 1024 * 1024)),
            Triple(0x2C, 0x07, ChipInfo("NPCX797WC", 512 * 1024)),
            Triple(0x25, 0x09, ChipInfo("NPCX993F", 512 * 1024)),
            Triple(0x21, 0x09, ChipInfo("NPCX996F", 512 * 1024)),
            Triple(0x22, 0x09, ChipInfo("NPCX997F", 1024 * 1024)),
            Triple(0x2B, 0x09, ChipInfo("NPCX99FP", 1024 * 1024)),
            Triple(0x62, 0x09, ChipInfo("NPCX997FB", 512 * 1024)),
        )

    fun lookupChip(
        deviceId: Int,
        chipId: Int,
    ): ChipInfo? = CHIP_TABLE.firstOrNull { it.first == deviceId && it.second == chipId }?.third

    // Response sizes for each command type
    fun syncResponseSize(): Int = 1

    fun writeResponseSize(): Int = 1

    fun readResponseSize(dataSize: Int): Int = dataSize + 3 // CMD + data + 2 CRC

    fun execResponseSize(): Int = 3
}

class FlashException(message: String) : Exception(message)
