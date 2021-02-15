package com.schibsted.account.android.webflows

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilTest {
    @Test
    fun queryEncodeShouldReturnQueryString() {
        val queryString = Util.queryEncode(mapOf("foo" to "bar", "abc" to "xyz"))
        assertEquals("foo=bar&abc=xyz", queryString)
    }

    @Test
    fun parseQueryParametersShouldReturnKeysAndValues() {
        val params = Util.parseQueryParameters("foo=bar&abc=xyz")
        assertEquals(mapOf("foo" to "bar", "abc" to "xyz"), params)
    }
}
