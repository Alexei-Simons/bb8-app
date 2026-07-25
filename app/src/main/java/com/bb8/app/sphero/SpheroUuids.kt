package com.bb8.app.sphero

import java.util.UUID

object SpheroUuids {
    private const val BASE = "22bb746f-%s-7554-2d6f-726568705327"

    val COMMAND: UUID = uuid("2ba1")
    val RESPONSE: UUID = uuid("2ba6")
    val ANTI_DOS: UUID = uuid("2bbd")
    val TX_POWER: UUID = uuid("2bb2")
    val WAKE: UUID = uuid("2bbf")

    private fun uuid(suffix: String): UUID = UUID.fromString(BASE.format(suffix))

    const val NAME_PREFIX = "BB-"
    const val ANTI_DOS_PAYLOAD = "011i3"
    const val TX_POWER_PAYLOAD: Byte = 7
    const val WAKE_PAYLOAD: Byte = 1
    const val WRITE_CHUNK_SIZE = 20
    const val WRITE_INTERVAL_MS = 60L
}
