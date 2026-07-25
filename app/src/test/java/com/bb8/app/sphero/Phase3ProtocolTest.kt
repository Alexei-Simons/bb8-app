package com.bb8.app.sphero

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SpheroMacroCompilerTest {

    @Test
    fun compile_rollRgbDelayEnd() {
        val bytecode = SpheroMacroCompiler.compile(
            listOf(
                MacroStep.Roll(120, 90),
                MacroStep.Rgb(255, 0, 0),
                MacroStep.DelayMs(500),
                MacroStep.Roll(0, 90),
            ),
        )
        assertArrayEquals(
            byteArrayOf(
                0x05, 120, 0x00, 90,
                0x07, 0xFF.toByte(), 0x00, 0x00,
                0x0B, 0x01, 0xF4.toByte(),
                0x05, 0x00, 0x00, 90,
                0x00,
            ),
            bytecode,
        )
    }
}

class SpheroAsyncParserTest {

    private val parser = SpheroAsyncParser()

    @Test
    fun append_parsesAsyncSensorStream() {
        val data = byteArrayOf(0x01, 0x00, 0x02, 0x00, 0x03)
        val packet = buildAsync(idCode = 0x03, data = data)
        val result = parser.append(packet)
        assertEquals(0, result.responses.size)
        assertEquals(1, result.asyncPackets.size)
        assertEquals(0x03, result.asyncPackets[0].idCode)
        assertArrayEquals(data, result.asyncPackets[0].data)
    }

    @Test
    fun append_parsesSyncResponse() {
        val packet = buildSync(seq = 2, data = byteArrayOf(0x01))
        val result = parser.append(packet)
        assertEquals(1, result.responses.size)
        assertEquals(2, result.responses[0].sequence)
    }

    private fun buildSync(seq: Int, data: ByteArray): ByteArray {
        val dlen = data.size + 1
        val frameBody = byteArrayOf(0x00, seq.toByte(), dlen.toByte()) + data
        val checksum = (0xFF - (frameBody.sumOf { it.toInt() and 0xFF } and 0xFF)) and 0xFF
        return byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + frameBody + checksum.toByte()
    }

    private fun buildAsync(idCode: Int, data: ByteArray): ByteArray {
        val dlen = data.size + 1
        val dlenMsb = ((dlen shr 8) and 0xFF).toByte()
        val dlenLsb = (dlen and 0xFF).toByte()
        val frameBody = byteArrayOf(idCode.toByte(), dlenMsb, dlenLsb) + data
        val checksum = (0xFF - (frameBody.sumOf { it.toInt() and 0xFF } and 0xFF)) and 0xFF
        return byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + frameBody + checksum.toByte()
    }
}

class SpheroSensorDecoderTest {

    @Test
    fun decode_locatorAndVelocity() {
        val decoder = SpheroSensorDecoder(0, SpheroStreamMasks.LOCATOR_AND_VELOCITY)
        val data = byteArrayOf(
            0x00, 0x64,
            0x00, 0x32,
            0x00, 0x0A,
            0x00, 0x05,
            0x00, 0x14,
        )
        val sample = decoder.decode(data)
        assertEquals(100f, sample.locatorXCm)
        assertEquals(50f, sample.locatorYCm)
        assertEquals(1.0f, sample.velocityXCmPerSec)
        assertEquals(0.5f, sample.velocityYCmPerSec)
        assertEquals(20f, sample.speedCmPerSec)
    }

    @Test
    fun setDataStreaming_payloadIs13Bytes() {
        val builder = SpheroPacketBuilder()
        val packet = SpheroCommands.setDataStreaming(
            builder,
            interval = 100,
            samplesPerPacket = 1,
            primaryMask = 0,
            packetCount = 0,
            extendedMask = SpheroStreamMasks.LOCATOR,
        )
        val dlen = packet.bytes[5].toInt() and 0xFF
        assertEquals(14, dlen)
        assertEquals(13, dlen - 1)
    }
}
