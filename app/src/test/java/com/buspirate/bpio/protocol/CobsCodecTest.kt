package com.buspirate.bpio.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CobsCodecTest {
    @Test
    fun encodeEmpty() {
        assertArrayEquals(byteArrayOf(0x01, 0x00), CobsCodec.encode(byteArrayOf()))
    }

    @Test
    fun encodeSingleZero() {
        assertArrayEquals(
            byteArrayOf(0x01, 0x01, 0x00),
            CobsCodec.encode(byteArrayOf(0x00)),
        )
    }

    @Test
    fun encodeSingleNonZero() {
        assertArrayEquals(
            byteArrayOf(0x02, 0x11, 0x00),
            CobsCodec.encode(byteArrayOf(0x11)),
        )
    }

    @Test
    fun encodeWikipediaExample1() {
        assertArrayEquals(
            byteArrayOf(0x01, 0x01, 0x01, 0x00),
            CobsCodec.encode(byteArrayOf(0x00, 0x00)),
        )
    }

    @Test
    fun encodeWikipediaExample2() {
        assertArrayEquals(
            byteArrayOf(0x03, 0x11, 0x22, 0x02, 0x33, 0x00),
            CobsCodec.encode(byteArrayOf(0x11, 0x22, 0x00, 0x33)),
        )
    }

    @Test
    fun encodeWikipediaExample3() {
        assertArrayEquals(
            byteArrayOf(0x05, 0x11, 0x22, 0x33, 0x44, 0x00),
            CobsCodec.encode(byteArrayOf(0x11, 0x22, 0x33, 0x44)),
        )
    }

    @Test
    fun encodeWikipediaExample4() {
        assertArrayEquals(
            byteArrayOf(0x02, 0x11, 0x01, 0x01, 0x01, 0x00),
            CobsCodec.encode(byteArrayOf(0x11, 0x00, 0x00, 0x00)),
        )
    }

    @Test
    fun decodeEmpty() {
        assertArrayEquals(byteArrayOf(), CobsCodec.decode(byteArrayOf(0x01, 0x00)))
    }

    @Test
    fun decodeSingleZero() {
        assertArrayEquals(
            byteArrayOf(0x00),
            CobsCodec.decode(byteArrayOf(0x01, 0x01, 0x00)),
        )
    }

    @Test
    fun decodeSingleNonZero() {
        assertArrayEquals(
            byteArrayOf(0x11),
            CobsCodec.decode(byteArrayOf(0x02, 0x11, 0x00)),
        )
    }

    @Test
    fun decodeWikipediaExample1() {
        assertArrayEquals(
            byteArrayOf(0x00, 0x00),
            CobsCodec.decode(byteArrayOf(0x01, 0x01, 0x01, 0x00)),
        )
    }

    @Test
    fun decodeWikipediaExample2() {
        assertArrayEquals(
            byteArrayOf(0x11, 0x22, 0x00, 0x33),
            CobsCodec.decode(byteArrayOf(0x03, 0x11, 0x22, 0x02, 0x33, 0x00)),
        )
    }

    @Test
    fun decodeWikipediaExample3() {
        assertArrayEquals(
            byteArrayOf(0x11, 0x22, 0x33, 0x44),
            CobsCodec.decode(byteArrayOf(0x05, 0x11, 0x22, 0x33, 0x44, 0x00)),
        )
    }

    @Test
    fun decodeWikipediaExample4() {
        assertArrayEquals(
            byteArrayOf(0x11, 0x00, 0x00, 0x00),
            CobsCodec.decode(byteArrayOf(0x02, 0x11, 0x01, 0x01, 0x01, 0x00)),
        )
    }

    @Test
    fun roundTrip() {
        val original = byteArrayOf(0x01, 0x00, 0x02, 0x00, 0x03)
        assertArrayEquals(original, CobsCodec.decode(CobsCodec.encode(original)))
    }

    @Test
    fun roundTripAllZeros() {
        val original = ByteArray(10) { 0x00 }
        assertArrayEquals(original, CobsCodec.decode(CobsCodec.encode(original)))
    }

    @Test
    fun roundTripNoZeros() {
        val original = ByteArray(10) { (it + 1).toByte() }
        assertArrayEquals(original, CobsCodec.decode(CobsCodec.encode(original)))
    }

    @Test
    fun roundTripLargePayload() {
        val original = ByteArray(300) { (it % 256).toByte() }
        assertArrayEquals(original, CobsCodec.decode(CobsCodec.encode(original)))
    }
}
