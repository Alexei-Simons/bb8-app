package com.bb8.app.sphero

/**
 * Sphero V1 sensor streaming (SET_DATA_STREAMING CID 0x11).
 * Reference: spherov2.py controls/v1.py, toy/sphero.py, commands/async_.py (ID 0x03).
 */
object SpheroStreamMasks {
    const val LOCATOR_X: Int = 0x0800_0000
    const val LOCATOR_Y: Int = 0x0400_0000
    const val VELOCITY_X: Int = 0x0100_0000
    const val VELOCITY_Y: Int = 0x0080_0000
    const val SPEED: Int = 0x0040_0000

    const val LOCATOR: Int = LOCATOR_X or LOCATOR_Y
    const val VELOCITY: Int = VELOCITY_X or VELOCITY_Y
    const val LOCATOR_AND_VELOCITY: Int = LOCATOR or VELOCITY or SPEED

    /** Default interval divisor: 400 Hz / 100 = 4 Hz */
    const val DEFAULT_INTERVAL: Int = 100
    const val DEFAULT_SAMPLES_PER_PACKET: Int = 1
}

data class SensorStreamSample(
    val locatorXCm: Float? = null,
    val locatorYCm: Float? = null,
    val velocityXCmPerSec: Float? = null,
    val velocityYCmPerSec: Float? = null,
    val speedCmPerSec: Float? = null,
)

class SpheroSensorDecoder(
    private val primaryMask: Int,
    private val extendedMask: Int,
) {
    private val fieldOrder: List<String> = buildFieldOrder(primaryMask, extendedMask)

    fun decode(data: ByteArray): SensorStreamSample {
        if (data.isEmpty() || fieldOrder.isEmpty()) return SensorStreamSample()

        var offset = 0
        var locatorX: Float? = null
        var locatorY: Float? = null
        var velocityX: Float? = null
        var velocityY: Float? = null
        var speed: Float? = null

        for (field in fieldOrder) {
            if (offset + 2 > data.size) break
            val raw = readInt16Be(data, offset)
            offset += 2
            when (field) {
                "locator_x" -> locatorX = raw.toFloat()
                "locator_y" -> locatorY = raw.toFloat()
                "velocity_x" -> velocityX = raw * 0.1f
                "velocity_y" -> velocityY = raw * 0.1f
                "speed" -> speed = raw.toFloat()
                else -> Unit
            }
        }

        return SensorStreamSample(
            locatorXCm = locatorX,
            locatorYCm = locatorY,
            velocityXCmPerSec = velocityX,
            velocityYCmPerSec = velocityY,
            speedCmPerSec = speed,
        )
    }

    companion object {
        internal fun buildFieldOrder(primaryMask: Int, extendedMask: Int): List<String> {
            val fields = mutableListOf<String>()
            for (bit in 31 downTo 0) {
                val mask = 1 shl bit
                if (primaryMask and mask != 0) {
                    fields.add("other")
                }
            }
            for (bit in 31 downTo 0) {
                val mask = 1 shl bit
                if (extendedMask and mask == 0) continue
                fields.add(
                    when (mask) {
                        SpheroStreamMasks.LOCATOR_X -> "locator_x"
                        SpheroStreamMasks.LOCATOR_Y -> "locator_y"
                        SpheroStreamMasks.VELOCITY_X -> "velocity_x"
                        SpheroStreamMasks.VELOCITY_Y -> "velocity_y"
                        SpheroStreamMasks.SPEED -> "speed"
                        else -> "other"
                    },
                )
            }
            return fields
        }

        private fun readInt16Be(data: ByteArray, offset: Int): Int {
            return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        }
    }
}
