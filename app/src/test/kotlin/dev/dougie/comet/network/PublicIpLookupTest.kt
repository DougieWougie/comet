package dev.dougie.comet.network

import org.junit.Assert.assertEquals
import org.junit.Test

class PublicIpLookupTest {
    @Test fun splitsAsnFromIsp() {
        val ip = PublicIpLookup.parse("""{"ip":"203.0.113.9","city":"Leeds","country":"GB","org":"AS2856 British Telecommunications PLC"}""")
        assertEquals("203.0.113.9", ip.ip)
        assertEquals("AS2856", ip.asn)
        assertEquals("British Telecommunications PLC", ip.isp)
        assertEquals("Leeds", ip.city)
    }

    @Test fun toleratesMissingFields() {
        val ip = PublicIpLookup.parse("""{"ip":"198.51.100.2"}""")
        assertEquals(null, ip.asn); assertEquals(null, ip.isp); assertEquals(null, ip.city)
    }
}
