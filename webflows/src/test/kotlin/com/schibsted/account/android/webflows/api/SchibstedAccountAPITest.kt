package com.schibsted.account.android.webflows.api

import com.schibsted.account.android.webflows.client.Environment
import junit.framework.TestCase
import org.junit.Test
import java.net.URL

class SchibstedAccountAPITest {

    @Test
    fun test() {
        val api = SchibstedAccountAPI(URL(Environment.PRE.url))
        api.tokenRequest("fake")
    }

    @Test
    fun testLoginUlr() {
        val api = SchibstedAccountAPI(URL(Environment.PRE.url))

    }
}