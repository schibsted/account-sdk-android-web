package com.schibsted.account.android.webflows

import com.schibsted.account.android.webflows.client.Environment
import com.schibsted.account.android.webflows.client.MfaType
import org.junit.Test

import org.junit.Assert.*

class ClientUnitTest {

    @Test
    fun loginUrlShouldBeCorrect() {
        val client = Client(Environment.PRE)
        val clientId = "dfasdfa"
        val redirectUri = "fsdfasdf"
        println(client.generateLoginUrl(clientId, redirectUri, MfaType.OTP, setOf()))
    }
}