package com.bb8.app.ble

enum class PowerState(val code: Int) {
    UNKNOWN(0),
    CHARGING(1),
    OK(2),
    LOW(3),
    CRITICAL(4),
    ;

    companion object {
        fun fromCode(code: Int): PowerState =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

enum class BatteryHealthLevel {
    GOOD,
    FAIR,
    POOR,
    CRITICAL,
    UNKNOWN,
}

data class BatteryHealth(
    val voltageVolts: Float,
    val powerState: PowerState,
    val chargeCycles: Int,
    val secondsSinceLastCharge: Int,
    val level: BatteryHealthLevel,
    val summary: String,
    val detail: String,
) {
    companion object {
        private const val FULL_VOLTAGE = 4.15f
        private const val HEALTHY_VOLTAGE = 3.75f
        private const val LOW_VOLTAGE = 3.55f
        private const val CRITICAL_VOLTAGE = 3.35f
        private const val MIN_PLAUSIBLE_VOLTAGE_RAW = 280  // 2.80V
        private const val MAX_PLAUSIBLE_VOLTAGE_RAW = 430  // 4.30V

        fun fromRaw(data: ByteArray): BatteryHealth? {
            if (data.size < 8) return null

            val recordVersion = data[0].toInt() and 0xFF
            val stateCode = data[1].toInt() and 0xFF
            val fieldA = readUint16Be(data, 2)
            val fieldB = readUint16Be(data, 4)
            val secondsSinceCharge = readUint16Be(data, 6)

            val (voltageRaw, chargeCycles) = resolveVoltageAndCycles(fieldA, fieldB)
            val voltage = voltageRaw / 100f
            val powerState = PowerState.fromCode(stateCode)
            val (level, summary, detail) = assess(voltage, powerState, chargeCycles, recordVersion, fieldA, fieldB)

            return BatteryHealth(
                voltageVolts = voltage,
                powerState = powerState,
                chargeCycles = chargeCycles,
                secondsSinceLastCharge = secondsSinceCharge,
                level = level,
                summary = summary,
                detail = detail,
            )
        }

        private fun readUint16Be(data: ByteArray, offset: Int): Int {
            return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        }

        /**
         * Sphero's struct is RecVer, State, Voltage, ChargeCycles, Time — but some V1 droids
         * (including BB-8) have been observed returning charge cycles before voltage.
         */
        private fun resolveVoltageAndCycles(fieldA: Int, fieldB: Int): Pair<Int, Int> {
            val aLooksLikeVoltage = fieldA in MIN_PLAUSIBLE_VOLTAGE_RAW..MAX_PLAUSIBLE_VOLTAGE_RAW
            val bLooksLikeVoltage = fieldB in MIN_PLAUSIBLE_VOLTAGE_RAW..MAX_PLAUSIBLE_VOLTAGE_RAW

            return when {
                aLooksLikeVoltage && !bLooksLikeVoltage -> fieldA to fieldB
                bLooksLikeVoltage && !aLooksLikeVoltage -> fieldB to fieldA
                aLooksLikeVoltage && bLooksLikeVoltage -> fieldA to fieldB
                else -> fieldA to fieldB
            }
        }

        private fun assess(
            voltage: Float,
            powerState: PowerState,
            chargeCycles: Int,
            recordVersion: Int,
            rawFieldA: Int,
            rawFieldB: Int,
        ): Triple<BatteryHealthLevel, String, String> {
            val level = when {
                powerState == PowerState.CRITICAL || voltage < CRITICAL_VOLTAGE ->
                    BatteryHealthLevel.CRITICAL
                powerState == PowerState.LOW || voltage < LOW_VOLTAGE ->
                    BatteryHealthLevel.POOR
                voltage < HEALTHY_VOLTAGE ->
                    BatteryHealthLevel.FAIR
                voltage >= HEALTHY_VOLTAGE && (powerState == PowerState.OK || powerState == PowerState.CHARGING) ->
                    BatteryHealthLevel.GOOD
                else -> BatteryHealthLevel.UNKNOWN
            }

            val percent = ((voltage - CRITICAL_VOLTAGE) / (FULL_VOLTAGE - CRITICAL_VOLTAGE))
                .coerceIn(0f, 1f)
            val percentText = (percent * 100).toInt()

            val summary = when (level) {
                BatteryHealthLevel.GOOD -> "Battery looks healthy (~$percentText%)"
                BatteryHealthLevel.FAIR -> "Battery is weak (~$percentText%)"
                BatteryHealthLevel.POOR -> "Battery is low — may die quickly"
                BatteryHealthLevel.CRITICAL -> "Battery critically low — likely failing"
                BatteryHealthLevel.UNKNOWN -> "Battery state unclear"
            }

            val warnings = buildList {
                if (voltage < LOW_VOLTAGE) {
                    add("Voltage ${"%.2f".format(voltage)}V is below safe operating range.")
                }
                if (powerState == PowerState.CRITICAL || powerState == PowerState.LOW) {
                    add("Droid reports ${powerState.name.lowercase()} power state.")
                }
                if (chargeCycles > 400) {
                    add("$chargeCycles charge cycles — cell is aged.")
                }
                if (level == BatteryHealthLevel.CRITICAL || level == BatteryHealthLevel.POOR) {
                    add("Warm housing + instant shutdown often means a swollen or dead LiPo.")
                    add("Replacement battery kits exist; do not puncture the ball.")
                }
            }

            val detail = buildString {
                append("Voltage: ${"%.2f".format(voltage)}V  |  State: ${powerState.name}")
                append("\nCharge cycles: $chargeCycles  |  FW record: v$recordVersion")
                if (voltage > 5f) {
                    append("\nRaw fields: 0x${rawFieldA.toString(16)} / 0x${rawFieldB.toString(16)} (unexpected)")
                }
                if (warnings.isNotEmpty()) {
                    append("\n\n")
                    warnings.forEachIndexed { index, warning ->
                        if (index > 0) append("\n")
                        append("• $warning")
                    }
                }
            }

            return Triple(level, summary, detail)
        }
    }
}
