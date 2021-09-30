package com.schibsted.account.webflows.persistence.compat

import android.util.Log
import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.testutil.await
import com.schibsted.account.testutil.withServer
import com.schibsted.account.webflows.api.ApiResult
import com.schibsted.account.webflows.api.CodeExchangeResponse
import com.schibsted.account.webflows.api.HttpError
import com.schibsted.account.webflows.api.SchibstedAccountApi
import com.schibsted.account.webflows.client.AuthState
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.token.TokenError
import com.schibsted.account.webflows.token.TokenHandler
import com.schibsted.account.webflows.token.TokenRequestResult
import com.schibsted.account.webflows.token.UserTokensResult
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import io.mockk.*
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.*


class MigratingSessionStorageTest {
    private val newClientId = "newClientId"
    private val legacyClientId = "legacyClientId"
    private val legacyClientSecret = "legacyClientSecret"

    @Before
    fun setup(){
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }

    @Test
    fun testStoreOnlyWritesToNewStorage() {
        val legacyStorage = mockk<LegacySessionStorage>(relaxUnitFun = true)
        val newStorage = mockk<SessionStorage>(relaxUnitFun = true)

        val migratingStorage = MigratingSessionStorage(
            client = mockk(),
            newStorage = newStorage,
            legacyStorage = legacyStorage,
            legacyClientId = legacyClientId,
            legacyClientSecret = legacyClientSecret
        )
        val userSession = StoredUserSession(newClientId, Fixtures.userTokens, Date())
        migratingStorage.save(StoredUserSession(newClientId, Fixtures.userTokens, Date()))

        verify { newStorage.save(userSession) }
        verify { legacyStorage wasNot Called }
    }

    @Test
    fun testRemoveOnlyRemovesFromNewStorage() {
        val legacyStorage = mockk<LegacySessionStorage>(relaxUnitFun = true)
        val newStorage = mockk<SessionStorage>(relaxUnitFun = true)

        val migratingStorage = MigratingSessionStorage(
            client = mockk(),
            newStorage = newStorage,
            legacyStorage = legacyStorage,
            legacyClientId = legacyClientId,
            legacyClientSecret = legacyClientSecret
        )
        migratingStorage.remove(newClientId)

        verify { newStorage.remove(newClientId) }
        verify { legacyStorage wasNot Called }
    }

    @Test
    fun testGetPrefersNewStorage() {
        val legacyStorage = mockk<LegacySessionStorage>(relaxUnitFun = true)

        val userSession = StoredUserSession(newClientId, Fixtures.userTokens, Date())
        val newStorage = mockk<SessionStorage>()
        every { newStorage.get(newClientId, any()) } answers {
            val callback = secondArg<(StoredUserSession?) -> Unit>()
            callback(userSession)
        }

        val migratingStorage = MigratingSessionStorage(
            client = mockk(),
            newStorage = newStorage,
            legacyStorage = legacyStorage,
            legacyClientId = legacyClientId,
            legacyClientSecret = legacyClientSecret
        )

        migratingStorage.get(newClientId) {
            assertEquals(userSession, it)
        }

        verify { legacyStorage wasNot Called }
    }

    @Test
    fun testGetMigratesExistingLegacySession() {
        val authCode = "test_auth_code"
        val codeExchangeResponseJson =
            """
            {
                "name": "SPP Container",
                "version": "0.2",
                "api": 2,
                "code": 200,
                "data": {"code": "$authCode"}
            }
        """.trimIndent()

        val legacyStorage = mockk<LegacySessionStorage>(relaxUnitFun = true)
        val legacyUserSession = StoredUserSession(legacyClientId, Fixtures.userTokens, Date())
        every { legacyStorage.get(legacyClientId) } returns legacyUserSession

        val newStorage = mockk<SessionStorage>(relaxUnitFun = true)
        every { newStorage.get(newClientId, any()) } answers {
            val callback = secondArg<(StoredUserSession?) -> Unit>()
            callback(null)
        }

        val tokenHandler = mockk<TokenHandler> {
            every {
                makeTokenRequest(
                    authCode,
                    null,
                    any()
                )
            } answers {
                val callback = thirdArg<(TokenRequestResult) -> Unit>()
                callback(Right(UserTokensResult(Fixtures.userTokens, null, 10)))
            }
        }

        val codeExchangeResponse = MockResponse().setBody(codeExchangeResponseJson)
        withServer(codeExchangeResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            await { done ->
                val client =
                    Fixtures.getClient(
                        clientConfiguration = Fixtures.clientConfig.copy(clientId = newClientId),
                        tokenHandler = tokenHandler,
                        schibstedAccountApi = schaccApi
                    )

                val migratingStorage = MigratingSessionStorage(
                    client = client,
                    newStorage = newStorage,
                    legacyStorage = legacyStorage,
                    legacyClientId = legacyClientId,
                    legacyClientSecret = legacyClientSecret
                )

                val expectedMigratedSession =
                    StoredUserSession(newClientId, Fixtures.userTokens, Date())
                migratingStorage.get(newClientId) {
                    assertEquals(expectedMigratedSession.clientId, it!!.clientId)
                    assertEquals(expectedMigratedSession.userTokens, it.userTokens)

                    verify { newStorage.get(newClientId, any()) }
                    verify { legacyStorage.get(legacyClientId) }
                    verify {
                        newStorage.save(match {
                            expectedMigratedSession.clientId == it.clientId &&
                                    expectedMigratedSession.userTokens == it.userTokens
                        })
                    }
                    verify { legacyStorage.remove() }

                    done()
                }
            }
        }
    }

    @Test
    fun testGetReturnsNullIfNoSessionExists() {
        val legacyStorage = mockk<LegacySessionStorage>()
        every { legacyStorage.get(legacyClientId) } returns null
        val newStorage = mockk<SessionStorage>()
        every { newStorage.get(newClientId, any()) } answers {
            val callback = secondArg<(StoredUserSession?) -> Unit>()
            callback(null)
        }

        val migratingStorage = MigratingSessionStorage(
            client = mockk(),
            newStorage = newStorage,
            legacyStorage = legacyStorage,
            legacyClientId = legacyClientId,
            legacyClientSecret = legacyClientSecret
        )
        migratingStorage.get(newClientId) {
            assertNull(it)
        }

        verify { newStorage.get(newClientId, any()) }
        verify { legacyStorage.get(legacyClientId) }
    }

    @Test
    fun `migrateSession should call callback with migratedSession if tokenRequest was sucessful`() {
        val legacyStorage = mockk<LegacySessionStorage> {
            every { remove() } just Runs
        }
        every { legacyStorage.get(legacyClientId) } returns null

        val response = Right(CodeExchangeResponse("code"))
        val legacyClient: LegacyClient = mockk {
            every { getAuthCodeFromTokens(any(), any(), any()) } answers {
                val authCodeTokenCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
                authCodeTokenCallback(response)
            }
        }

        val newStorage = mockk<SessionStorage> {
            every { get(newClientId, any()) } answers {
                val callback = secondArg<(StoredUserSession?) -> Unit>()
                callback(null)
            }
            every { save(any()) } just Runs
        }

        val legacySession: StoredUserSession = mockk{
            every { userTokens } returns mockk()
        }
        val migratedSession = StoredUserSession("clientId", mockk(), Date())
        val client = mockk<Client> {
            every { configuration } returns mockk()
            every { configuration.clientId } returns "clientId"
            every { makeTokenRequest(response.value.code, null, any()) } answers {
                val callback = thirdArg<(Either<TokenError, StoredUserSession>) -> Unit>()
                callback(Right(migratedSession))
            }
        }
        val migratingStorage = MigratingSessionStorage(
            client = client,
            newStorage = newStorage,
            legacyStorage = legacyStorage,
            legacyClientId = legacyClientId,
            legacyClientSecret = legacyClientSecret
        )
        val callback = mockk<(StoredUserSession?) -> Unit>()
        every { callback(any()) } just Runs

        migratingStorage.migrateSession(legacySession, legacyClient, callback)

        verify(exactly = 1) { callback(migratedSession) }
    }

    @Test
    fun `migrateSession should call callback with null if tokenRequest was not sucessful`() {
        val legacyStorage = mockk<LegacySessionStorage> {
            every { remove() } just Runs
        }
        every { legacyStorage.get(legacyClientId) } returns null

        val response = Right(CodeExchangeResponse("code"))
        val legacyClient: LegacyClient = mockk {
            every { getAuthCodeFromTokens(any(), any(), any()) } answers {
                val authCodeTokenCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
                authCodeTokenCallback(response)
            }
        }

        val newStorage = mockk<SessionStorage> {
            every { get(newClientId, any()) } answers {
                val callback = secondArg<(StoredUserSession?) -> Unit>()
                callback(null)
            }
            every { save(any()) } just Runs
        }

        val legacySession: StoredUserSession = mockk{
            every { userTokens } returns mockk()
        }
        val migrationError = TokenError.TokenRequestError(mockk())
        val client = mockk<Client> {
            every { configuration } returns mockk()
            every { configuration.clientId } returns "clientId"
            every { makeTokenRequest(response.value.code, null, any()) } answers {
                val callback = thirdArg<(Either<TokenError, StoredUserSession>) -> Unit>()
                callback(Left(migrationError))
            }
        }
        val migratingStorage = MigratingSessionStorage(
            client = client,
            newStorage = newStorage,
            legacyStorage = legacyStorage,
            legacyClientId = legacyClientId,
            legacyClientSecret = legacyClientSecret
        )
        val callback = mockk<(StoredUserSession?) -> Unit>()
        every { callback(any()) } just Runs

        migratingStorage.migrateSession(legacySession, legacyClient, callback)

        verify(exactly = 1) { callback(null) }
    }

    @Test
    fun `migrateSession should call callback with null if recieved callback fetching auth code was holding an error`() {
        val legacyStorage = mockk<LegacySessionStorage> {
            every { remove() } just Runs
        }
        every { legacyStorage.get(legacyClientId) } returns null

        val errorResponse = Left(HttpError.ErrorResponse(300, "body"))
        val legacyClient: LegacyClient = mockk {
            every { getAuthCodeFromTokens(any(), any(), any()) } answers {
                val authCodeTokenCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
                authCodeTokenCallback(errorResponse)
            }
        }

        val newStorage = mockk<SessionStorage> {
            every { get(newClientId, any()) } answers {
                val callback = secondArg<(StoredUserSession?) -> Unit>()
                callback(null)
            }
            every { save(any()) } just Runs
        }

        val legacySession: StoredUserSession = mockk{
            every { userTokens } returns mockk()
        }
        val client = mockk<Client> {
            every { configuration } returns mockk()
            every { configuration.clientId } returns "clientId"
        }
        val migratingStorage = MigratingSessionStorage(
            client = client,
            newStorage = newStorage,
            legacyStorage = legacyStorage,
            legacyClientId = legacyClientId,
            legacyClientSecret = legacyClientSecret
        )
        val callback = mockk<(StoredUserSession?) -> Unit>()
        every { callback(any()) } just Runs

        migratingStorage.migrateSession(legacySession, legacyClient, callback)

        verify(exactly = 1) { callback(null) }
    }

}
