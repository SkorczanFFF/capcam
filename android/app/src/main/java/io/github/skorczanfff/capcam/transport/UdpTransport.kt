package io.github.skorczanfff.capcam.transport

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/** Resolves [host] in the constructor, so create it off the UI thread. */
class UdpTransport(host: String, port: Int) : Transport {

    private val address = InetSocketAddress(host, port).also {
        require(!it.isUnresolved) { "Can't resolve $host" }
    }
    private val socket = DatagramSocket()
    private val packet = DatagramPacket(ByteArray(0), 0, address)

    override fun send(data: ByteArray, length: Int) {
        packet.setData(data, 0, length)
        socket.send(packet)
    }

    override fun close() = socket.close()
}
