package com.green3077.remotecontrol.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    /** Best-effort local Wi-Fi/LAN IPv4 address, or null if none is up. */
    fun findLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress }
                .firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
