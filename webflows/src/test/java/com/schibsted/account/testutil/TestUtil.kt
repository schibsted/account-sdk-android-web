package com.schibsted.account.testutil

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.RSAKey
import com.schibsted.account.webflows.token.IdTokenValidatorTest
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun await(timeoutSeconds: Long = 1, func: (() -> Unit) -> Unit) {
    val latch = CountDownLatch(1)

    try {
        func {
            latch.countDown()
        }
        Assert.assertTrue(latch.await(timeoutSeconds, TimeUnit.SECONDS))
    } catch (e: Throwable) {
        throw e
    }
}

fun <L, R> Either<L, R>.assertRight(func: (R) -> Unit) {
    Assert.assertTrue("$this is not a Right", this is Right)
    func((this as Right).value)
}

fun <L, R> Either<L, R>.assertLeft(func: (L) -> Unit) {
    Assert.assertTrue("$this is not a Left", this is Left)
    func((this as Left).value)
}

fun withServer(vararg responses: MockResponse, func: (MockWebServer) -> Unit) {
    val server = MockWebServer()

    for (r in responses) {
        server.enqueue(r)
    }

    server.start()
    try {
        func(server)
    } finally {
        server.shutdown()
    }
}

fun createJws(key: RSAKey, keyId: String, payload: Payload): String {
    val header = JWSHeader.Builder(JWSAlgorithm.RS256)
        .keyID(keyId)
        .build()
    val jwsObject = JWSObject(header, payload)

    jwsObject.sign(RSASSASigner(key))
    return jwsObject.serialize()
}
