package com.bb8.app.sphero

data class SpheroResponse(
    val resultCode: Int,
    val sequence: Int,
    val data: ByteArray,
)

class SpheroResponseParser {
    private val sop = 0xFF

    private var buffer = byteArrayOf()

    fun reset() {
        buffer = byteArrayOf()
    }

    fun append(chunk: ByteArray): List<SpheroResponse> {
        if (chunk.isEmpty()) return emptyList()
        buffer += chunk

        val responses = mutableListOf<SpheroResponse>()
        while (buffer.size > 4) {
            if (buffer[0] != sop.toByte()) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }

            val sop2 = buffer[1].toInt() and 0xFF
            if (sop2 != sop) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }

            // Response frame after SOP SOP: MRSP, SEQ, DLEN, <data>, CHK
            val dlen = buffer[4].toInt() and 0xFF
            val packetSize = 2 + dlen + 3
            if (buffer.size < packetSize) break

            val frameWithChecksum = buffer.copyOfRange(2, packetSize)
            val checksum = frameWithChecksum.last().toInt() and 0xFF
            val frameBody = frameWithChecksum.copyOfRange(0, frameWithChecksum.size - 1)
            if (checksum != packetChecksum(frameBody)) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }

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
        return responses
    }

    private fun packetChecksum(payload: ByteArray): Int {
        val sum = payload.fold(0) { acc, byte -> acc + (byte.toInt() and 0xFF) }
        return (0xFF - (sum and 0xFF)) and 0xFF
    }
}
