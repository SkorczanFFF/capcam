package io.github.skorczanfff.capcam.transport

import java.io.Closeable

/** Sends one packet to the PC. Implementations may block briefly; never call from the UI thread. */
interface Transport : Closeable {
    fun send(data: ByteArray, length: Int)
}
