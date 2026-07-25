package com.bb8.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryHealthTest {

    @Test
    fun fromRaw_parsesNormalVoltageAndCycles() {
        val fixed = byteArrayOf(
            0x01,
            PowerState.OK.code.toByte(),
            0x01, 0x8B.toByte(),
            0x01, 0x8F.toByte(),
            0x00, 0x3C,
        )
        val health = BatteryHealth.fromRaw(fixed)
        assertNotNull(health)
        assertEquals(3.95f, health!!.voltageVolts, 0.01f)
        assertEquals(399, health.chargeCycles)
        assertEquals(PowerState.OK, health.powerState)
    }

    @Test
    fun fromRaw_swapsSwappedVoltageAndCycleFields() {
        // BB-8 style: cycles in voltage slot (827), voltage in cycles slot (395)
        val data = byteArrayOf(
            0x01,
            PowerState.CRITICAL.code.toByte(),
            0x03, 0x3B, // 827 cycles misread as voltage field
            0x01, 0x8B.toByte(), // 395 -> 3.95V
            0x00, 0x00,
        )
        val health = BatteryHealth.fromRaw(data)
        assertNotNull(health)
        assertEquals(3.95f, health!!.voltageVolts, 0.01f)
        assertEquals(827, health.chargeCycles)
        assertEquals(BatteryHealthLevel.CRITICAL, health.level)
        assertTrue(health.diagnosticsOnly)
    }

    @Test
    fun diagnosticsOnly_whenChargingWithLowVoltage() {
        val data = byteArrayOf(
            0x01,
            PowerState.CHARGING.code.toByte(),
            0x01, 0x40.toByte(), // 3.20V
            0x00, 0x64,
            0x00, 0x00,
        )
        val health = BatteryHealth.fromRaw(data)
        assertNotNull(health)
        assertTrue(health!!.diagnosticsOnly)
    }

    @Test
    fun fromRaw_returnsNullForShortPayload() {
        assertEquals(null, BatteryHealth.fromRaw(byteArrayOf(0x01, 0x02)))
    }
}
