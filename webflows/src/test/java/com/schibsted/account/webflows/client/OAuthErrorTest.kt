package com.schibsted.account.webflows.client

import org.junit.Assert.*
import org.junit.Test

class OAuthErrorTest {
    @Test
    fun shouldParseOAuthJson() {
        val parsed =
            OAuthError.fromJson("""{"error": "invalid_request", "error_description": "test"}""")
        assertEquals("invalid_request", parsed?.error)
        assertEquals("test", parsed?.errorDescription)
    }

    @Test
    fun shouldGracefullyHandleMissingOauthAttributes() {
        assertNull(OAuthError.fromJson("""{"foo": "bar"}"""))
        assertNull(OAuthError.fromJson("{}"))
    }

    @Test
    fun shouldGracefullyHandleNonJson() {
        assertNull(OAuthError.fromJson("<html>Test</html>"))
    }
}
