package io.github.skorczanfff.capcam.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ipv4Test {

    @Test
    fun parsesDottedAddresses() {
        assertEquals(0xC0A86503.toInt(), Ipv4.parse("192.168.101.3"))
        assertEquals(0, Ipv4.parse("0.0.0.0"))
        assertEquals(-1, Ipv4.parse("255.255.255.255"))
        assertEquals(0x0A050002, Ipv4.parse(" 10.5.0.2 "))
    }

    @Test
    fun rejectsAnythingElse() {
        for (bad in listOf("", "192.168.1", "192.168.1.256", "a.b.c.d", "192.168.1.1.1", "192.168..1", "pc.local", "1234.1.1.1")) {
            assertNull(bad, Ipv4.parse(bad))
        }
    }

    @Test
    fun sameSubnetUsesThePrefix() {
        assertEquals(true, Ipv4.sameSubnet("192.168.101.3", "192.168.101.27", 24))
        assertEquals(false, Ipv4.sameSubnet("192.168.1.20", "192.168.101.27", 24))
        // The VPN address we hit while setting up: not on the home network.
        assertEquals(false, Ipv4.sameSubnet("10.5.0.2", "192.168.101.27", 24))
        assertEquals(true, Ipv4.sameSubnet("10.0.200.1", "10.0.3.4", 16))
        assertEquals(true, Ipv4.sameSubnet("192.168.101.27", "192.168.101.27", 32))
        assertEquals(false, Ipv4.sameSubnet("192.168.101.28", "192.168.101.27", 32))
    }

    @Test
    fun sameSubnetIsUnknownForHostNames() {
        assertNull(Ipv4.sameSubnet("my-pc.local", "192.168.101.27", 24))
        assertNull(Ipv4.sameSubnet("192.168.101.3", "192.168.101.27", 0))
    }

    @Test
    fun commonPrefixForWholeOctets() {
        assertEquals("192.168.101.", Ipv4.commonPrefix("192.168.101.27", 24))
        assertEquals("10.0.", Ipv4.commonPrefix("10.0.3.4", 16))
        assertNull(Ipv4.commonPrefix("192.168.101.27", 23))
        assertNull(Ipv4.commonPrefix("not an ip", 24))
    }
}
