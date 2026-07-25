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
    const val ANIMATRONIC: Int = 0x17
}

object SpheroCommandIds {
    const val PING: Int = 0x01
    const val SET_HEADING: Int = 0x01
    const val SET_STABILIZATION: Int = 0x02
    const val SET_DATA_STREAMING: Int = 0x11
    const val CONFIGURE_LOCATOR: Int = 0x13
    const val GET_POWER_STATE: Int = 0x20
    const val SET_MAIN_LED: Int = 0x20
    const val SET_INACTIVITY_TIMEOUT: Int = 0x25
    const val ROLL: Int = 0x30
    const val BOOST: Int = 0x31
    const val RUN_MACRO: Int = 0x50
    const val SAVE_TEMP_MACRO: Int = 0x51
    const val INIT_MACRO_EXECUTIVE: Int = 0x54
    const val ABORT_MACRO: Int = 0x55
    const val PLAY_ANIMATION: Int = 0x05
    const val STOP_ANIMATION: Int = 0x2B
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

    fun boost(builder: SpheroPacketBuilder): SpheroPacket {
        val data = byteArrayOf(1, 0, 0)
        return builder.build(SpheroDeviceIds.SPHERO, SpheroCommandIds.BOOST, data)
    }

    fun runMacro(builder: SpheroPacketBuilder, macroId: Int): SpheroPacket {
        val data = byteArrayOf(macroId.coerceIn(0, 255).toByte())
        return builder.build(SpheroDeviceIds.SPHERO, SpheroCommandIds.RUN_MACRO, data)
    }

    fun initMacroExecutive(builder: SpheroPacketBuilder): SpheroPacket =
        builder.build(SpheroDeviceIds.SPHERO, SpheroCommandIds.INIT_MACRO_EXECUTIVE)

    fun abortMacro(builder: SpheroPacketBuilder): SpheroPacket =
        builder.build(SpheroDeviceIds.SPHERO, SpheroCommandIds.ABORT_MACRO)

    fun saveTempMacro(builder: SpheroPacketBuilder, flags: Int, bytecode: ByteArray): SpheroPacket {
        val data = byteArrayOf(SpheroMacroSlots.TEMP.toByte(), flags.toByte()) + bytecode
        return builder.build(SpheroDeviceIds.SPHERO, SpheroCommandIds.SAVE_TEMP_MACRO, data)
    }

    fun configureLocator(
        builder: SpheroPacketBuilder,
        flags: Int = 0,
        x: Int = 0,
        y: Int = 0,
        yawTare: Int = 0,
    ): SpheroPacket {
        val data = byteArrayOf(
            flags.toByte(),
            ((x shr 8) and 0xFF).toByte(),
            (x and 0xFF).toByte(),
            ((y shr 8) and 0xFF).toByte(),
            (y and 0xFF).toByte(),
            ((yawTare shr 8) and 0xFF).toByte(),
            (yawTare and 0xFF).toByte(),
        )
        return builder.build(SpheroDeviceIds.SPHERO, SpheroCommandIds.CONFIGURE_LOCATOR, data)
    }

    fun setDataStreaming(
        builder: SpheroPacketBuilder,
        interval: Int,
        samplesPerPacket: Int,
        primaryMask: Int,
        packetCount: Int,
        extendedMask: Int,
    ): SpheroPacket {
        val data = byteArrayOf(
            ((interval shr 8) and 0xFF).toByte(),
            (interval and 0xFF).toByte(),
            ((samplesPerPacket shr 8) and 0xFF).toByte(),
            (samplesPerPacket and 0xFF).toByte(),
            ((primaryMask shr 24) and 0xFF).toByte(),
            ((primaryMask shr 16) and 0xFF).toByte(),
            ((primaryMask shr 8) and 0xFF).toByte(),
            (primaryMask and 0xFF).toByte(),
            packetCount.coerceIn(0, 255).toByte(),
            ((extendedMask shr 24) and 0xFF).toByte(),
            ((extendedMask shr 16) and 0xFF).toByte(),
            ((extendedMask shr 8) and 0xFF).toByte(),
            (extendedMask and 0xFF).toByte(),
        )
        return builder.build(SpheroDeviceIds.SPHERO, SpheroCommandIds.SET_DATA_STREAMING, data)
    }

    fun enableLocatorStreaming(builder: SpheroPacketBuilder): SpheroPacket =
        setDataStreaming(
            builder,
            interval = SpheroStreamMasks.DEFAULT_INTERVAL,
            samplesPerPacket = SpheroStreamMasks.DEFAULT_SAMPLES_PER_PACKET,
            primaryMask = 0,
            packetCount = 0,
            extendedMask = SpheroStreamMasks.LOCATOR_AND_VELOCITY,
        )

    fun playAnimation(builder: SpheroPacketBuilder, animationId: Int): SpheroPacket {
        val id = animationId.coerceIn(0, 65535)
        val data = byteArrayOf(
            ((id shr 8) and 0xFF).toByte(),
            (id and 0xFF).toByte(),
        )
        return builder.build(SpheroDeviceIds.ANIMATRONIC, SpheroCommandIds.PLAY_ANIMATION, data)
    }

    fun stopAnimation(builder: SpheroPacketBuilder): SpheroPacket =
        builder.build(SpheroDeviceIds.ANIMATRONIC, SpheroCommandIds.STOP_ANIMATION)

    fun disableDataStreaming(builder: SpheroPacketBuilder): SpheroPacket =
        setDataStreaming(builder, 0, 0, 0, 0, 0)
}
