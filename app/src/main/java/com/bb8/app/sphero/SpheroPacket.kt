package com.bb8.app.sphero

enum class RollMode(val code: Int) {
    STOP(0),
    GO(1),
    CALIBRATE(2),
}

enum class ReverseFlag(val code: Int) {
    OFF(0),
    ON(1),
}

object SpheroDeviceIds {
    const val CORE: Int = 0x00
    const val SPHERO: Int = 0x02
}

object SpheroCommandIds {
    const val PING: Int = 0x01
    const val SET_HEADING: Int = 0x01
    const val SET_STABILIZATION: Int = 0x02
    const val GET_POWER_STATE: Int = 0x20
    const val SET_MAIN_LED: Int = 0x20
    const val SET_INACTIVITY_TIMEOUT: Int = 0x25
    const val ROLL: Int = 0x30
}

data class SpheroPacket(
    val bytes: ByteArray,
    val sequence: Int,
)

class SpheroPacketBuilder {
    private var sequence: Int = 0

    fun build(did: Int, cid: Int, data: ByteArray = byteArrayOf()): SpheroPacket {
        val seq = sequence and 0xFF
        val dlen = data.size + 1
        val payload = ByteArray(4 + data.size)
        payload[0] = did.toByte()
        payload[1] = cid.toByte()
        payload[2] = seq.toByte()
        payload[3] = dlen.toByte()
        data.copyInto(payload, destinationOffset = 4)

        sequence = (sequence + 1) and 0xFF

        val checksum = packetChecksum(payload)
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + payload + checksum.toByte()
        return SpheroPacket(bytes, seq)
    }

    fun chunks(packet: SpheroPacket): List<ByteArray> = chunks(packet.bytes)

    fun chunks(packet: ByteArray): List<ByteArray> {
        return packet.toList()
            .chunked(SpheroUuids.WRITE_CHUNK_SIZE)
            .map { it.toByteArray() }
    }

    private fun packetChecksum(payload: ByteArray): Byte {
        val sum = payload.fold(0) { acc, byte -> acc + (byte.toInt() and 0xFF) }
        return (0xFF - (sum and 0xFF)).toByte()
    }
}

object SpheroCommands {
    fun ping(builder: SpheroPacketBuilder): SpheroPacket =
        builder.build(SpheroDeviceIds.CORE, SpheroCommandIds.PING)

    fun setInactivityTimeout(builder: SpheroPacketBuilder, seconds: Int): SpheroPacket {
        val clamped = seconds.coerceIn(0, 65535)
        val data = byteArrayOf(
            ((clamped shr 8) and 0xFF).toByte(),
            (clamped and 0xFF).toByte(),
        )
        return builder.build(SpheroDeviceIds.CORE, SpheroCommandIds.SET_INACTIVITY_TIMEOUT, data)
    }

    fun getPowerState(builder: SpheroPacketBuilder): SpheroPacket =
        builder.build(SpheroDeviceIds.CORE, SpheroCommandIds.GET_POWER_STATE)

    fun roll(
        builder: SpheroPacketBuilder,
        speed: Int,
        heading: Int,
        mode: RollMode,
        reverse: ReverseFlag,
    ): SpheroPacket {
        val clampedSpeed = speed.coerceIn(0, 255)
        val normalizedHeading = ((heading % 360) + 360) % 360
        val data = byteArrayOf(
            clampedSpeed.toByte(),
            ((normalizedHeading shr 8) and 0xFF).toByte(),
            (normalizedHeading and 0xFF).toByte(),
            mode.code.toByte(),
            reverse.code.toByte(),
        )
        return builder.build(SpheroDeviceIds.SPHERO, SpheroCommandIds.ROLL, data)
    }

    fun setMainLed(builder: SpheroPacketBuilder, r: Int, g: Int, b: Int): SpheroPacket {
        val data = byteArrayOf(
            r.coerceIn(0, 255).toByte(),
            g.coerceIn(0, 255).toByte(),
            b.coerceIn(0, 255).toByte(),
        )
        return builder.build(SpheroDeviceIds.SPHERO, SpheroCommandIds.SET_MAIN_LED, data)
    }

    fun setHeading(builder: SpheroPacketBuilder, heading: Int): SpheroPacket {
        val normalizedHeading = ((heading % 360) + 360) % 360
        val data = byteArrayOf(
            ((normalizedHeading shr 8) and 0xFF).toByte(),
            (normalizedHeading and 0xFF).toByte(),
        )
        return builder.build(SpheroDeviceIds.SPHERO, SpheroCommandIds.SET_HEADING, data)
    }

    fun setStabilization(builder: SpheroPacketBuilder, enabled: Boolean): SpheroPacket {
        val data = byteArrayOf(if (enabled) 1 else 0)
        return builder.build(SpheroDeviceIds.SPHERO, SpheroCommandIds.SET_STABILIZATION, data)
    }

    fun calibrateHeading(builder: SpheroPacketBuilder, heading: Int): SpheroPacket =
        roll(builder, 0, heading, RollMode.CALIBRATE, ReverseFlag.OFF)
}
