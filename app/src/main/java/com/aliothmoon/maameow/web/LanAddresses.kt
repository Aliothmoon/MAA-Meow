package com.aliothmoon.maameow.web

import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface

data class LanAddress(val interfaceName: String, val host: String)

object LanAddresses {

    // 蜂窝口拿到的是运营商内网地址，别人连不进来
    private val CELLULAR_PREFIXES = listOf("rmnet", "ccmni", "pdp", "v4-rmnet")

    /** Wi-Fi 与热点排前面，隧道（tun）次之 */
    fun ipv4(): List<LanAddress> = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && CELLULAR_PREFIXES.none(it.name::startsWith) }
            .flatMap { nif ->
                nif.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { addr -> addr.hostAddress?.let { LanAddress(nif.name, it) } }
            }
            .sortedBy { rank(it.interfaceName) }
    } catch (e: Exception) {
        Timber.w(e, "WebUi: list interfaces failed")
        emptyList()
    }

    fun url(host: String, port: Int) = "http://$host:$port"

    private fun rank(name: String) = when {
        name.startsWith("wlan") -> 0
        name.startsWith("ap") || name.startsWith("swlan") -> 1
        name.startsWith("tun") -> 2
        else -> 3
    }
}
