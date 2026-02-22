package me.danielschaefer.android.buspirate.protocol

object CobsCodec {
    fun encode(data: ByteArray): ByteArray {
        val maxOut = data.size + (data.size / 254) + 2
        val dst = ByteArray(maxOut)
        var dstIdx = 1
        var codeIdx = 0
        var code = 1

        for (byte in data) {
            if (byte == 0.toByte()) {
                dst[codeIdx] = code.toByte()
                codeIdx = dstIdx++
                code = 1
            } else {
                dst[dstIdx++] = byte
                code++
                if (code == 0xFF) {
                    dst[codeIdx] = code.toByte()
                    codeIdx = dstIdx++
                    code = 1
                }
            }
        }

        dst[codeIdx] = code.toByte()
        dst[dstIdx++] = 0x00
        return dst.copyOf(dstIdx)
    }

    fun decode(data: ByteArray): ByteArray {
        require(data.isNotEmpty()) { "Input must not be empty" }
        val dst = ByteArray(data.size)
        var srcIdx = 0
        var dstIdx = 0

        while (srcIdx < data.size) {
            val code = data[srcIdx++].toInt() and 0xFF
            if (code == 0) break

            for (i in 1 until code) {
                require(srcIdx < data.size) { "Unexpected end of input" }
                val b = data[srcIdx++]
                require(b != 0.toByte()) { "Unexpected zero in encoded data" }
                dst[dstIdx++] = b
            }

            if (code < 0xFF && srcIdx < data.size && data[srcIdx] != 0.toByte()) {
                dst[dstIdx++] = 0
            }
        }

        return dst.copyOf(dstIdx)
    }
}
