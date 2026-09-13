package com.green3077.remotecontrol.util

import java.security.SecureRandom

object PinGenerator {
    private val random = SecureRandom()

    /** A fresh 6-digit pairing PIN, regenerated every time the host starts sharing. */
    fun generate(): String {
        val value = random.nextInt(1_000_000)
        return value.toString().padStart(6, '0')
    }
}
