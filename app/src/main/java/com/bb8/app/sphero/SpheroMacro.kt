package com.bb8.app.sphero

/**
 * Sphero V1 macro bytecode (host-compiled).
 * Opcodes from node-sphero-pwn-macros / Sphero API 1.x.
 */
object SpheroMacroOpcodes {
    const val END: Int = 0x00
    const val ROLL: Int = 0x05
    const val RGB: Int = 0x07
    const val DELAY: Int = 0x0B
}

object SpheroMacroFlags {
    const val BRAKE_ON_END: Int = 0x01
    const val EXCLUSIVE_DRIVE: Int = 0x02
}

object SpheroMacroSlots {
    const val TEMP: Int = 0xFF
}

sealed class MacroStep {
    data class Roll(val speed: Int, val heading: Int) : MacroStep()
    data class Rgb(val r: Int, val g: Int, val b: Int) : MacroStep()
    data class DelayMs(val millis: Int) : MacroStep()
}

object SpheroMacroCompiler {
    fun compile(steps: List<MacroStep>): ByteArray {
        val bytes = mutableListOf<Byte>()
        for (step in steps) {
            when (step) {
                is MacroStep.Roll -> {
                    val heading = ((step.heading % 360) + 360) % 360
                    bytes += SpheroMacroOpcodes.ROLL.toByte()
                    bytes += step.speed.coerceIn(0, 255).toByte()
                    bytes += ((heading shr 8) and 0xFF).toByte()
                    bytes += (heading and 0xFF).toByte()
                }
                is MacroStep.Rgb -> {
                    bytes += SpheroMacroOpcodes.RGB.toByte()
                    bytes += step.r.coerceIn(0, 255).toByte()
                    bytes += step.g.coerceIn(0, 255).toByte()
                    bytes += step.b.coerceIn(0, 255).toByte()
                }
                is MacroStep.DelayMs -> {
                    val ms = step.millis.coerceIn(0, 65535)
                    bytes += SpheroMacroOpcodes.DELAY.toByte()
                    bytes += ((ms shr 8) and 0xFF).toByte()
                    bytes += (ms and 0xFF).toByte()
                }
            }
        }
        bytes += SpheroMacroOpcodes.END.toByte()
        return bytes.toByteArray()
    }
}
