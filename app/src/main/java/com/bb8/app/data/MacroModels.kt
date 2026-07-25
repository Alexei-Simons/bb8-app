package com.bb8.app.data

import com.bb8.app.sphero.MacroStep

data class SavedMacro(
    val id: String,
    val name: String,
    val steps: List<MacroStepDto>,
    val createdAtMs: Long = System.currentTimeMillis(),
)

data class MacroStepDto(
    val type: String,
    val speed: Int = 0,
    val heading: Int = 0,
    val r: Int = 0,
    val g: Int = 0,
    val b: Int = 0,
    val delayMs: Int = 0,
) {
    fun toMacroStep(): MacroStep? = when (type) {
        "roll" -> MacroStep.Roll(speed, heading)
        "rgb" -> MacroStep.Rgb(r, g, b)
        "delay" -> MacroStep.DelayMs(delayMs)
        else -> null
    }

    companion object {
        fun from(step: MacroStep): MacroStepDto = when (step) {
            is MacroStep.Roll -> MacroStepDto(type = "roll", speed = step.speed, heading = step.heading)
            is MacroStep.Rgb -> MacroStepDto(type = "rgb", r = step.r, g = step.g, b = step.b)
            is MacroStep.DelayMs -> MacroStepDto(type = "delay", delayMs = step.millis)
        }
    }
}
