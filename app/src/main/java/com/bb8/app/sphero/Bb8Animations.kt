package com.bb8.app.sphero

/**
 * Animatronic animation IDs (DID 0x17). BB-8 firmware may accept a subset of these;
 * IDs align with the BB-9E / Star Wars droid animatronic API.
 */
enum class Bb8Animation(val id: Int, val label: String) {
    SCAN_SWEEP(2, "Scan"),
    YES(4, "Yes"),
    EXCITED(9, "Excited"),
    GREETINGS(11, "Hello"),
    SLEEP(14, "Sleep"),
    SURPRISED(15, "Surprised"),
    SHAKE(35, "Shake"),
    HAPPY(26, "Happy"),
    SAD(33, "Sad"),
}
