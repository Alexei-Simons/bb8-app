package com.bb8.app.sphero

import com.bb8.app.ble.PowerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpheroResponseParserTest {

    private val parser = SpheroResponseParser()

    @Test
    fun append_parsesPowerStateResponse() {
        val data = byteArrayOf(
            0x01,
            PowerState.OK.code.toByte(),
            0x01, 0x8B.toByte(),
            0x01, 0x00,
            0x00, 0x10,
        )
        val packet = buildResponse(seq = 5, data = data)
        val responses = parser.append(packet)
        assertEquals(1, responses.size)
        assertEquals(0, responses[0].resultCode)
        assertEquals(5, responses[0].sequence)
        assertEquals(8, responses[0].data.size)
    }

    @Test
    fun append_handlesMultiplePacketsInOneChunk() {
        val chunk = buildResponse(1, byteArrayOf(0x01)) + buildResponse(2, byteArrayOf(0x02))
        val responses = parser.append(chunk)
        assertEquals(2, responses.size)
        assertEquals(1, responses[0].sequence)
        assertEquals(2, responses[1].sequence)
    }

    @Test
    fun reset_clearsPartialBuffer() {
        parser.append(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00))
        parser.reset()
        val responses = parser.append(byteArrayOf())
        assertTrue(responses.isEmpty())
    }

    private fun buildResponse(seq: Int, data: ByteArray): ByteArray {
        val dlen = data.size + 1
        val frameBody = byteArrayOf(0x00, seq.toByte(), dlen.toByte()) + data
        val checksum = (0xFF - (frameBody.sumOf { it.toInt() and 0xFF } and 0xFF)) and 0xFF
        return byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + frameBody + checksum.toByte()
    }
}
