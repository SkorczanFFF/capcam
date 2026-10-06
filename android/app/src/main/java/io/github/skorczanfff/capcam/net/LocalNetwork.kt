package io.github.skorczanfff.capcam.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** The phone's own IPv4 address on Wi-Fi/Ethernet, and whether a VPN is on. */
data class LocalNetwork(val address: String?, val prefixLength: Int, val vpnActive: Boolean) {

    companion object {
        fun read(context: Context): LocalNetwork {
            val cm = context.getSystemService(ConnectivityManager::class.java)
                ?: return LocalNetwork(null, 0, false)
            var vpn = false
            var address: String? = null
            var prefix = 0
            @Suppress("DEPRECATION") // allNetworks is the simplest way to see every network, VPN included.
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true
                    continue
                }
                val local = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                if (!local || address != null) continue
                val link = cm.getLinkProperties(network)?.linkAddresses
                    ?.firstOrNull { it.address is Inet4Address } ?: continue
                address = link.address.hostAddress
                prefix = link.prefixLength
            }
            return LocalNetwork(address, prefix, vpn)
        }
    }
}

/** Plain IPv4 helpers, kept free of Android types so they can be unit tested. */
object Ipv4 {

    /** "192.168.1.20" → its 32-bit value; null for anything that isn't a dotted IPv4 address. */
    fun parse(text: String): Int? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var value = 0
        for (p in parts) {
            if (p.isEmpty() || p.length > 3 || !p.all(Char::isDigit)) return null
            val octet = p.toInt()
            if (octet > 255) return null
            value = (value shl 8) or octet
        }
        return value
    }

    /** True/false when both are IPv4 addresses, null when [host] isn't one (e.g. a host name). */
    fun sameSubnet(host: String, phone: String, prefixLength: Int): Boolean? {
        val a = parse(host) ?: return null
        val b = parse(phone) ?: return null
        if (prefixLength !in 1..32) return null
        val mask = if (prefixLength == 32) -1 else ((1L shl 32) - (1L shl (32 - prefixLength))).toInt()
        return (a and mask) == (b and mask)
    }

    /** "192.168.101." for a /24 network: what the PC's address should start with. Null otherwise. */
    fun commonPrefix(phone: String, prefixLength: Int): String? {
        if (parse(phone) == null) return null
        val octets = prefixLength / 8
        if (prefixLength % 8 != 0 || octets !in 1..3) return null
        return phone.split('.').take(octets).joinToString(".", postfix = ".")
    }
}
