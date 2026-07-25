package com.bb8.app.sphero

/**
 * Parses sync responses (SOP 0xFF 0xFF) and async packets (SOP 0xFF 0xFE).
 * Reference: spherov2.py controls/v1.py Packet.Collector
 */
class SpheroAsyncParser {
    private var buffer = byteArrayOf()

    fun reset() {
        buffer = byteArrayOf()
    }

    fun append(chunk: ByteArray): NotificationParseResult {
        if (chunk.isEmpty()) return NotificationParseResult.EMPTY

        val responses = mutableListOf<SpheroResponse>()
        val asyncPackets = mutableListOf<SpheroAsyncPacket>()

        buffer += chunk
        while (buffer.size > 4) {
            if ((buffer[0].toInt() and 0xFF) != SOP) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }

            val sop2 = buffer[1].toInt() and 0xFF
            when (sop2) {
                SOP -> {
                    if (buffer.size < 5) break
                    val dlen = buffer[4].toInt() and 0xFF
                    val packetSize = 2 + dlen + 3
                    if (buffer.size < packetSize) break

                    val frameWithChecksum = buffer.copyOfRange(2, packetSize)
                    if (!verifyChecksum(frameWithChecksum)) {
                        buffer = buffer.copyOfRange(1, buffer.size)
                        continue
                    }

                    val frameBody = frameWithChecksum.copyOfRange(0, frameWithChecksum.size - 1)
                    val mrsp = frameBody[0].toInt() and 0xFF
                    val seq = frameBody[1].toInt() and 0xFF
                    val data = if (frameBody.size > 3) {
                        frameBody.copyOfRange(3, frameBody.size)
                    } else {
                        byteArrayOf()
                    }
                    responses.add(SpheroResponse(mrsp, seq, data))
                    buffer = buffer.copyOfRange(packetSize, buffer.size)
                }
                ASYNC -> {
                    if (buffer.size < 6) break
                    val dlenMsb = buffer[3].toInt() and 0xFF
                    val dlenLsb = buffer[4].toInt() and 0xFF
                    val dlen = (dlenMsb shl 8) or dlenLsb
                    val packetSize = 2 + dlen + 3
                    if (buffer.size < packetSize) break

                    val frameWithChecksum = buffer.copyOfRange(2, packetSize)
                    if (!verifyChecksum(frameWithChecksum)) {
                        buffer = buffer.copyOfRange(1, buffer.size)
                        continue
                    }

                    val frameBody = frameWithChecksum.copyOfRange(0, frameWithChecksum.size - 1)
                    val idCode = frameBody[0].toInt() and 0xFF
                    val data = if (frameBody.size > 3) {
                        frameBody.copyOfRange(3, frameBody.size)
                    } else {
                        byteArrayOf()
                    }
                    asyncPackets.add(SpheroAsyncPacket(idCode, data))
                    buffer = buffer.copyOfRange(packetSize, buffer.size)
                }
                else -> {
                    buffer = buffer.copyOfRange(1, buffer.size)
                }
            }
        }

        return NotificationParseResult(responses, asyncPackets)
    }

    private fun verifyChecksum(frameWithChecksum: ByteArray): Boolean {
        if (frameWithChecksum.isEmpty()) return false
        val checksum = frameWithChecksum.last().toInt() and 0xFF
        val frameBody = frameWithChecksum.copyOfRange(0, frameWithChecksum.size - 1)
        return checksum == packetChecksum(frameBody)
    }

    private fun packetChecksum(payload: ByteArray): Int {
        val sum = payload.fold(0) { acc, byte -> acc + (byte.toInt() and 0xFF) }
        return (0xFF - (sum and 0xFF)) and 0xFF
    }

    companion object {
        private const val SOP = 0xFF
        private const val ASYNC = 0xFE
    }
}

data class SpheroAsyncPacket(
    val idCode: Int,
    val data: ByteArray,
)

data class NotificationParseResult(
    val responses: List<SpheroResponse>,
    val asyncPackets: List<SpheroAsyncPacket>,
) {
    companion object {
        val EMPTY = NotificationParseResult(emptyList(), emptyList())
    }
}
