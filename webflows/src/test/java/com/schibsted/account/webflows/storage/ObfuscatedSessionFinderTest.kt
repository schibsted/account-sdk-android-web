package com.schibsted.account.webflows.storage

import android.util.Log
import com.google.gson.GsonBuilder
import com.schibsted.account.webflows.persistence.ObfuscatedSessionFinder
import io.mockk.mockkStatic
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ObfuscatedSessionFinderTest {

    // Gson is used to serialize and deserialize objects
    // We mimick the Gson object used in the SessionStorage class
    private val gson = GsonBuilder().setDateFormat("MM dd, yyyy HH:mm:ss").create()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
    }

    @Test
    fun `given null user session return null for Either Right onSuccess`() {
        // given
        val clientId = CLIENT_ID
        val userSessionJson = null

        // when
        val storedUserSession =
            ObfuscatedSessionFinder.getDeobfuscatedStoredUserSessionIfViable(
                gson,
                clientId,
                userSessionJson
            )

        // then
        storedUserSession.onSuccess {
            Assert.assertNull(it)
        }.onFailure {
            assertNotNull(it)
        }
    }

    @Test
    fun `given null user session return Storage Error for Either Left onFailure`() {
        // given
        val clientId = CLIENT_ID
        val userSessionJson = null

        // when
        val storedUserSession =
            ObfuscatedSessionFinder.getDeobfuscatedStoredUserSessionIfViable(
                gson,
                clientId,
                userSessionJson
            )

        // then
        storedUserSession.onSuccess {
            Assert.assertNull(it)
        }.onFailure {
            assertNotNull(it)
        }
    }

    @Test
    fun `given obfuscated user session return deobfuscated user session object`() {
        //given
        val clientId = CLIENT_ID
        val userSessionJson = OBFUSCATED_USER_SESSION_JSON

        // when
        val storedUserSession =
            ObfuscatedSessionFinder.getDeobfuscatedStoredUserSessionIfViable(
                gson,
                clientId,
                userSessionJson
            )

        // then
        storedUserSession.onSuccess {
            assertEquals(CLIENT_ID, it?.clientId)
            assertEquals(ACCESS_TOKEN_OBFUSCATED, it?.userTokens?.accessToken)
            assertEquals(ID_TOKEN_OBFUSCATED, it?.userTokens?.idToken)
            assertEquals(REFRESH_TOKEN_OBFUSCATED, it?.userTokens?.refreshToken)
            assertNotNull(it)
        }.onFailure {
            Assert.assertNull(it)
        }
    }

    @Test
    fun `given not obfuscated user session return deobfuscated user session object`() {
        // given
        val clientId = CLIENT_ID
        val userSessionJson = NOT_OBFUSCATED_USER_SESSION_JSON

        // when
        val storedUserSession =
            ObfuscatedSessionFinder.getDeobfuscatedStoredUserSessionIfViable(
                gson,
                clientId,
                userSessionJson
            )

        // then
        storedUserSession.onSuccess {
            assertEquals(CLIENT_ID, it?.clientId)
            assertEquals(ACCESS_TOKEN_NOT_OBFUSCATED, it?.userTokens?.accessToken)
            assertEquals(ID_TOKEN_NOT_OBFUSCATED, it?.userTokens?.idToken)
            assertEquals(REFRESH_TOKEN_NOT_OBFUSCATED, it?.userTokens?.refreshToken)
            assertNotNull(it)
        }.onFailure {
            Assert.assertNull(it)
        }
    }

    @Test
    fun `given malformed not obfuscated user session return not null data`() {
        // given
        val clientId = CLIENT_ID
        val userSessionJson = MALFORMED_NOT_OBFUSCATED_USER_SESSION_JSON

        // when
        val storedUserSession = ObfuscatedSessionFinder.getDeobfuscatedStoredUserSessionIfViable(
            gson,
            clientId,
            userSessionJson
        )

        // then
        storedUserSession.onSuccess {
            assertNotNull(it)
        }.onFailure {
            Assert.assertNull(it)
        }
    }

    @Test
    fun `given malformed obfuscated user session return not null data`() {
        // given
        val clientId = CLIENT_ID
        val userSessionJson = MALFORMED_OBFUSCATED_USER_SESSION_JSON

        // when
        val storedUserSession = ObfuscatedSessionFinder.getDeobfuscatedStoredUserSessionIfViable(
            gson,
            clientId,
            userSessionJson
        )

        // then
        storedUserSession.onSuccess {
            assertNotNull(it)
        }.onFailure {
            Assert.assertNull(it)
        }
    }



    /*****
     * DEBUG VALUES FOR TOKENS:
     * private const val ACCESS_TOKEN =
     *             "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyMTMzODc3LCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzEyMTMzODE3LCJjbGllbnRfaWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJqdGkiOiJlZDQzYTMwNi1mOTBkLTRjYmUtYTU1ZC1lMzM3ZjM3MzhlZjEifQ.sckisjhfp-BoB379HI2cnPeDEH1XJMBghcBBRtDC4vY"
     *
     *             {
     *   "aud": "602525f2b41fa31789a95aa8",
     *   "sub": "bcd62f69-aaeb-5179-b81a-3e245cd779af",
     *   "user_id": "12007107",
     *   "scope": "openid offline_access",
     *   "iss": "https://identity-pre.schibsted.com/",
     *   "exp": 1712133877,
     *   "type": "access",
     *   "iat": 1712133817,
     *   "client_id": "602525f2b41fa31789a95aa8",
     *   "jti": "ed43a306-f90d-4cbe-a55d-e337f3738ef1"
     * }
     *
     *
     * private const val ID_TOKEN =
     *             "eyJraWQiOiJjN2Y2MDM2OS04MDMyLTQ1MWEtODcxZC1iNDhkMzA0YTJiMzUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJhY3IiOiIwIiwibGVnYWN5X3VzZXJfaWQiOiIxMjAwNzEwNyIsImFtciI6WyJwd2QiXSwiYXV0aF90aW1lIjoxNzEyMTMzODA4LCJpc3MiOiJodHRwczpcL1wvaWRlbnRpdHktcHJlLnNjaGlic3RlZC5jb21cLyIsImV4cCI6MTcxMjEzNzQxNywiaWF0IjoxNzEyMTMzODE3LCJub25jZSI6IjRlTEhtWG81RVQifQ.NXIQw4lWp3FXc8MzQNmE5frV-KsmOG9JeKQBt9bCYcrBAHMvhw2bQQwm9bpjM-y36GGxtFanZGz6hyitZl_YGwU_FuM2XXWG1r5L1J2v2rFCfMnZdYfj-to28lK4JiYDU2-rc_eywdbzSbmQtps7qRxHWYTjf3gbkU5kEguYNMopncpk77pzRT3jjaBuzIPGoLBjLFG54FTKCFeeVZf-H8lSEz8-8x-p7WczLjroDHrOYdGznm9MytllN5sO1hR5d4_j6AiWfV_cyo2DadCoVcyeuI7lOoZMsP5B_HTiemj6c_C5Zl6ePKoc_8dvKGnmG6ArbQNdx1bcUbZ5m5KHwg"
     *
     *  {
     *   "sub": "bcd62f69-aaeb-5179-b81a-3e245cd779af",
     *   "aud": "602525f2b41fa31789a95aa8",
     *   "acr": "0",
     *   "legacy_user_id": "12007107",
     *   "amr": [
     *     "pwd"
     *   ],
     *   "auth_time": 1712133808,
     *   "iss": "https://identity-pre.schibsted.com/",
     *   "exp": 1712137417,
     *   "iat": 1712133817,
     *   "nonce": "4eLHmXo5ET"
     * }
     *
     * private const val REFRESH_TOKEN =
     *             "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyNzM4NjE3LCJ0eXBlIjoicmVmcmVzaCIsImlhdCI6MTcxMjEzMzgxNywiY2xpZW50X2lkIjoiNjAyNTI1ZjJiNDFmYTMxNzg5YTk1YWE4IiwianRpIjoiYWNkNWNkNmUtMTNkNS00NTYxLTg5NjYtMzhlYTY3NjNmNGQ3Iiwic2lkIjoiYnA4bWsifQ.N1nyl_9C0-ClPb_eSytXxi_cOrRVZEWjYKU6kz8klVU"
     *
     *             {
     *   "aud": "602525f2b41fa31789a95aa8",
     *   "sub": "bcd62f69-aaeb-5179-b81a-3e245cd779af",
     *   "user_id": "12007107",
     *   "scope": "openid offline_access",
     *   "iss": "https://identity-pre.schibsted.com/",
     *   "exp": 1712738617,
     *   "type": "refresh",
     *   "iat": 1712133817,
     *   "client_id": "602525f2b41fa31789a95aa8",
     *   "jti": "acd5cd6e-13d5-4561-8966-38ea6763f4d7",
     *   "sid": "bp8mk"
     * }
     *
     */

    @Test
    fun `given tokens return filled idTokenClaims`() {
        // given
        val refreshToken = REFRESH_TOKEN
        val accessToken = ACCESS_TOKEN
        val idToken = ID_TOKEN
        // when
        val idTokenClaims = ObfuscatedSessionFinder.createIdTokenClaimsBasedOnTokenJsons(refreshToken, accessToken, idToken)

        // then
        assertEquals("https://identity-pre.schibsted.com/", idTokenClaims.iss)
        assertEquals("bcd62f69-aaeb-5179-b81a-3e245cd779af", idTokenClaims.sub)
        assertEquals("12007107", idTokenClaims.userId)
        assertEquals("602525f2b41fa31789a95aa8", idTokenClaims.aud[0])
        assertEquals(1712137417L, idTokenClaims.exp)
        assertEquals("4eLHmXo5ET", idTokenClaims.nonce)
        assertEquals("pwd", idTokenClaims.amr?.get(0) ?: "")
    }


    companion object {
        private const val CLIENT_ID = "123"
        private const val ACCESS_TOKEN_OBFUSCATED =
            "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyMTMzODc3LCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzEyMTMzODE3LCJjbGllbnRfaWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJqdGkiOiJlZDQzYTMwNi1mOTBkLTRjYmUtYTU1ZC1lMzM3ZjM3MzhlZjEifQ.sckisjhfp-BoB379HI2cnPeDEH1XJMBghcBBRtDC4vY"
        private const val ID_TOKEN_OBFUSCATED =
            "eyJraWQiOiJjN2Y2MDM2OS04MDMyLTQ1MWEtODcxZC1iNDhkMzA0YTJiMzUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJhY3IiOiIwIiwibGVnYWN5X3VzZXJfaWQiOiIxMjAwNzEwNyIsImFtciI6WyJwd2QiXSwiYXV0aF90aW1lIjoxNzEyMTMzODA4LCJpc3MiOiJodHRwczpcL1wvaWRlbnRpdHktcHJlLnNjaGlic3RlZC5jb21cLyIsImV4cCI6MTcxMjEzNzQxNywiaWF0IjoxNzEyMTMzODE3LCJub25jZSI6IjRlTEhtWG81RVQifQ.NXIQw4lWp3FXc8MzQNmE5frV-KsmOG9JeKQBt9bCYcrBAHMvhw2bQQwm9bpjM-y36GGxtFanZGz6hyitZl_YGwU_FuM2XXWG1r5L1J2v2rFCfMnZdYfj-to28lK4JiYDU2-rc_eywdbzSbmQtps7qRxHWYTjf3gbkU5kEguYNMopncpk77pzRT3jjaBuzIPGoLBjLFG54FTKCFeeVZf-H8lSEz8-8x-p7WczLjroDHrOYdGznm9MytllN5sO1hR5d4_j6AiWfV_cyo2DadCoVcyeuI7lOoZMsP5B_HTiemj6c_C5Zl6ePKoc_8dvKGnmG6ArbQNdx1bcUbZ5m5KHwg"
        private const val REFRESH_TOKEN_OBFUSCATED =
            "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyNzM4NjE3LCJ0eXBlIjoicmVmcmVzaCIsImlhdCI6MTcxMjEzMzgxNywiY2xpZW50X2lkIjoiNjAyNTI1ZjJiNDFmYTMxNzg5YTk1YWE4IiwianRpIjoiYWNkNWNkNmUtMTNkNS00NTYxLTg5NjYtMzhlYTY3NjNmNGQ3Iiwic2lkIjoiYnA4bWsifQ.N1nyl_9C0-ClPb_eSytXxi_cOrRVZEWjYKU6kz8klVU"

        private const val OBFUSCATED_USER_SESSION_JSON = "{\n" +
                "   \"a\":\"123\",\n" +
                "   \"b\":\"04 03, 2024 10:43:37\",\n" +
                "   \"d\":{\n" +
                "      \"e\":\"eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyMTMzODc3LCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzEyMTMzODE3LCJjbGllbnRfaWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJqdGkiOiJlZDQzYTMwNi1mOTBkLTRjYmUtYTU1ZC1lMzM3ZjM3MzhlZjEifQ.sckisjhfp-BoB379HI2cnPeDEH1XJMBghcBBRtDC4vY\",\n" +
                "      \"f\":\"eyJraWQiOiJjN2Y2MDM2OS04MDMyLTQ1MWEtODcxZC1iNDhkMzA0YTJiMzUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJhY3IiOiIwIiwibGVnYWN5X3VzZXJfaWQiOiIxMjAwNzEwNyIsImFtciI6WyJwd2QiXSwiYXV0aF90aW1lIjoxNzEyMTMzODA4LCJpc3MiOiJodHRwczpcL1wvaWRlbnRpdHktcHJlLnNjaGlic3RlZC5jb21cLyIsImV4cCI6MTcxMjEzNzQxNywiaWF0IjoxNzEyMTMzODE3LCJub25jZSI6IjRlTEhtWG81RVQifQ.NXIQw4lWp3FXc8MzQNmE5frV-KsmOG9JeKQBt9bCYcrBAHMvhw2bQQwm9bpjM-y36GGxtFanZGz6hyitZl_YGwU_FuM2XXWG1r5L1J2v2rFCfMnZdYfj-to28lK4JiYDU2-rc_eywdbzSbmQtps7qRxHWYTjf3gbkU5kEguYNMopncpk77pzRT3jjaBuzIPGoLBjLFG54FTKCFeeVZf-H8lSEz8-8x-p7WczLjroDHrOYdGznm9MytllN5sO1hR5d4_j6AiWfV_cyo2DadCoVcyeuI7lOoZMsP5B_HTiemj6c_C5Zl6ePKoc_8dvKGnmG6ArbQNdx1bcUbZ5m5KHwg\",\n" +
                "      \"g\":{\n" +
                "         \"h\":[\n" +
                "            \"pwd\"\n" +
                "         ],\n" +
                "         \"i\":[\n" +
                "            \"602525f2b41fa31789a95aa8\"\n" +
                "         ],\n" +
                "         \"j\":1712137417,\n" +
                "         \"k\":\"https://identity-pre.schibsted.com/\",\n" +
                "         \"l\":\"4eLHmXo5ET\",\n" +
                "         \"m\":\"bcd62f69-aaeb-5179-b81a-3e245cd779af\",\n" +
                "         \"n\":\"12007107\"\n" +
                "      },\n" +
                "      \"c\":\"eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyNzM4NjE3LCJ0eXBlIjoicmVmcmVzaCIsImlhdCI6MTcxMjEzMzgxNywiY2xpZW50X2lkIjoiNjAyNTI1ZjJiNDFmYTMxNzg5YTk1YWE4IiwianRpIjoiYWNkNWNkNmUtMTNkNS00NTYxLTg5NjYtMzhlYTY3NjNmNGQ3Iiwic2lkIjoiYnA4bWsifQ.N1nyl_9C0-ClPb_eSytXxi_cOrRVZEWjYKU6kz8klVU\"\n" +
                "   }\n" +
                "}"

        private const val ACCESS_TOKEN_NOT_OBFUSCATED =
            "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyMTMzODc3LCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzEyMTMzODE3LCJjbGllbnRfaWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJqdGkiOiJlZDQzYTMwNi1mOTBkLTRjYmUtYTU1ZC1lMzM3ZjM3MzhlZjEifQ.sckisjhfp-BoB379HI2cnPeDEH1XJMBghcBBRtDC4vY"
        private const val ID_TOKEN_NOT_OBFUSCATED =
            "eyJraWQiOiJjN2Y2MDM2OS04MDMyLTQ1MWEtODcxZC1iNDhkMzA0YTJiMzUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJhY3IiOiIwIiwibGVnYWN5X3VzZXJfaWQiOiIxMjAwNzEwNyIsImFtciI6WyJwd2QiXSwiYXV0aF90aW1lIjoxNzEyMTMzODA4LCJpc3MiOiJodHRwczpcL1wvaWRlbnRpdHktcHJlLnNjaGlic3RlZC5jb21cLyIsImV4cCI6MTcxMjEzNzQxNywiaWF0IjoxNzEyMTMzODE3LCJub25jZSI6IjRlTEhtWG81RVQifQ.NXIQw4lWp3FXc8MzQNmE5frV-KsmOG9JeKQBt9bCYcrBAHMvhw2bQQwm9bpjM-y36GGxtFanZGz6hyitZl_YGwU_FuM2XXWG1r5L1J2v2rFCfMnZdYfj-to28lK4JiYDU2-rc_eywdbzSbmQtps7qRxHWYTjf3gbkU5kEguYNMopncpk77pzRT3jjaBuzIPGoLBjLFG54FTKCFeeVZf-H8lSEz8-8x-p7WczLjroDHrOYdGznm9MytllN5sO1hR5d4_j6AiWfV_cyo2DadCoVcyeuI7lOoZMsP5B_HTiemj6c_C5Zl6ePKoc_8dvKGnmG6ArbQNdx1bcUbZ5m5KHwg"
        private const val REFRESH_TOKEN_NOT_OBFUSCATED =
            "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyNzM4NjE3LCJ0eXBlIjoicmVmcmVzaCIsImlhdCI6MTcxMjEzMzgxNywiY2xpZW50X2lkIjoiNjAyNTI1ZjJiNDFmYTMxNzg5YTk1YWE4IiwianRpIjoiYWNkNWNkNmUtMTNkNS00NTYxLTg5NjYtMzhlYTY3NjNmNGQ3Iiwic2lkIjoiYnA4bWsifQ.N1nyl_9C0-ClPb_eSytXxi_cOrRVZEWjYKU6kz8klVU"

        private const val NOT_OBFUSCATED_USER_SESSION_JSON = "{\n" +
                "   \"clientId\":\"123\",\n" +
                "   \"updatedAt\":\"04 03, 2024 10:43:37\",\n" +
                "   \"userTokens\":{\n" +
                "      \"accessToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyMTMzODc3LCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzEyMTMzODE3LCJjbGllbnRfaWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJqdGkiOiJlZDQzYTMwNi1mOTBkLTRjYmUtYTU1ZC1lMzM3ZjM3MzhlZjEifQ.sckisjhfp-BoB379HI2cnPeDEH1XJMBghcBBRtDC4vY\",\n" +
                "      \"idToken\":\"eyJraWQiOiJjN2Y2MDM2OS04MDMyLTQ1MWEtODcxZC1iNDhkMzA0YTJiMzUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJhY3IiOiIwIiwibGVnYWN5X3VzZXJfaWQiOiIxMjAwNzEwNyIsImFtciI6WyJwd2QiXSwiYXV0aF90aW1lIjoxNzEyMTMzODA4LCJpc3MiOiJodHRwczpcL1wvaWRlbnRpdHktcHJlLnNjaGlic3RlZC5jb21cLyIsImV4cCI6MTcxMjEzNzQxNywiaWF0IjoxNzEyMTMzODE3LCJub25jZSI6IjRlTEhtWG81RVQifQ.NXIQw4lWp3FXc8MzQNmE5frV-KsmOG9JeKQBt9bCYcrBAHMvhw2bQQwm9bpjM-y36GGxtFanZGz6hyitZl_YGwU_FuM2XXWG1r5L1J2v2rFCfMnZdYfj-to28lK4JiYDU2-rc_eywdbzSbmQtps7qRxHWYTjf3gbkU5kEguYNMopncpk77pzRT3jjaBuzIPGoLBjLFG54FTKCFeeVZf-H8lSEz8-8x-p7WczLjroDHrOYdGznm9MytllN5sO1hR5d4_j6AiWfV_cyo2DadCoVcyeuI7lOoZMsP5B_HTiemj6c_C5Zl6ePKoc_8dvKGnmG6ArbQNdx1bcUbZ5m5KHwg\",\n" +
                "      \"idTokenClaims\":{\n" +
                "         \"amr\":[\n" +
                "            \"pwd\"\n" +
                "         ],\n" +
                "         \"aud\":[\n" +
                "            \"602525f2b41fa31789a95aa8\"\n" +
                "         ],\n" +
                "         \"exp\":1712137417,\n" +
                "         \"iss\":\"https://identity-pre.schibsted.com/\",\n" +
                "         \"nonce\":\"4eLHmXo5ET\",\n" +
                "         \"sub\":\"bcd62f69-aaeb-5179-b81a-3e245cd779af\",\n" +
                "         \"userId\":\"12007107\"\n" +
                "      },\n" +
                "      \"refreshToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyNzM4NjE3LCJ0eXBlIjoicmVmcmVzaCIsImlhdCI6MTcxMjEzMzgxNywiY2xpZW50X2lkIjoiNjAyNTI1ZjJiNDFmYTMxNzg5YTk1YWE4IiwianRpIjoiYWNkNWNkNmUtMTNkNS00NTYxLTg5NjYtMzhlYTY3NjNmNGQ3Iiwic2lkIjoiYnA4bWsifQ.N1nyl_9C0-ClPb_eSytXxi_cOrRVZEWjYKU6kz8klVU\"\n" +
                "   }\n" +
                "}"

        // Simply comment any line to check the test case
        private const val MALFORMED_NOT_OBFUSCATED_USER_SESSION_JSON = "{\n" +
                "   \"a\":\"123\",\n" +
                "   \"b\":\"04 03, 2024 10:43:37\",\n" +
                "   \"d\":{\n" +
                "      \"e\":\"eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyMTMzODc3LCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzEyMTMzODE3LCJjbGllbnRfaWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJqdGkiOiJlZDQzYTMwNi1mOTBkLTRjYmUtYTU1ZC1lMzM3ZjM3MzhlZjEifQ.sckisjhfp-BoB379HI2cnPeDEH1XJMBghcBBRtDC4vY\",\n" +
               // "      \"f\":\"eyJraWQiOiJjN2Y2MDM2OS04MDMyLTQ1MWEtODcxZC1iNDhkMzA0YTJiMzUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJhY3IiOiIwIiwibGVnYWN5X3VzZXJfaWQiOiIxMjAwNzEwNyIsImFtciI6WyJwd2QiXSwiYXV0aF90aW1lIjoxNzEyMTMzODA4LCJpc3MiOiJodHRwczpcL1wvaWRlbnRpdHktcHJlLnNjaGlic3RlZC5jb21cLyIsImV4cCI6MTcxMjEzNzQxNywiaWF0IjoxNzEyMTMzODE3LCJub25jZSI6IjRlTEhtWG81RVQifQ.NXIQw4lWp3FXc8MzQNmE5frV-KsmOG9JeKQBt9bCYcrBAHMvhw2bQQwm9bpjM-y36GGxtFanZGz6hyitZl_YGwU_FuM2XXWG1r5L1J2v2rFCfMnZdYfj-to28lK4JiYDU2-rc_eywdbzSbmQtps7qRxHWYTjf3gbkU5kEguYNMopncpk77pzRT3jjaBuzIPGoLBjLFG54FTKCFeeVZf-H8lSEz8-8x-p7WczLjroDHrOYdGznm9MytllN5sO1hR5d4_j6AiWfV_cyo2DadCoVcyeuI7lOoZMsP5B_HTiemj6c_C5Zl6ePKoc_8dvKGnmG6ArbQNdx1bcUbZ5m5KHwg\",\n" +
                "      \"g\":{\n" +
                "         \"h\":[\n" +
                "            \"pwd\"\n" +
                "         ],\n" +
                "         \"i\":[\n" +
                "            \"602525f2b41fa31789a95aa8\"\n" +
                "         ],\n" +
                "         \"j\":1712137417,\n" +
                "         \"k\":\"https://identity-pre.schibsted.com/\",\n" +
                "         \"l\":\"4eLHmXo5ET\",\n" +
                "         \"m\":\"bcd62f69-aaeb-5179-b81a-3e245cd779af\",\n" +
                "         \"n\":\"12007107\"\n" +
                "      },\n" +
                "      \"c\":\"eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyNzM4NjE3LCJ0eXBlIjoicmVmcmVzaCIsImlhdCI6MTcxMjEzMzgxNywiY2xpZW50X2lkIjoiNjAyNTI1ZjJiNDFmYTMxNzg5YTk1YWE4IiwianRpIjoiYWNkNWNkNmUtMTNkNS00NTYxLTg5NjYtMzhlYTY3NjNmNGQ3Iiwic2lkIjoiYnA4bWsifQ.N1nyl_9C0-ClPb_eSytXxi_cOrRVZEWjYKU6kz8klVU\"\n" +
                "   }\n" +
                "}"

        // Simply comment any line to check the test case
        private const val MALFORMED_OBFUSCATED_USER_SESSION_JSON = "{\n" +
                "   \"a\":\"123\",\n" +
                "   \"b\":{\n" +
               // "      \"d\":\"eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyNTYyMDc5LCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzEyNTYyMDE5LCJjbGllbnRfaWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJqdGkiOiI2NjYwNzA0NS0xMzQwLTQ1OTktOTM2My1jM2M0MjdjMWRiMWIifQ.ABPt3TbnD2v_5zDaPK5X5djnV_sXqDkq9u802Q2BN-c\",\n" +
                "      \"e\":\"eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEzMTY2ODE5LCJ0eXBlIjoicmVmcmVzaCIsImlhdCI6MTcxMjU2MjAxOSwiY2xpZW50X2lkIjoiNjAyNTI1ZjJiNDFmYTMxNzg5YTk1YWE4IiwianRpIjoiMTYyYzkwMmQtMjdhYi00MGYwLWJjMjktM2U2NjczZWRiMTExIiwic2lkIjoiUms2bGsifQ.ym_eEVNrPgfb10wP1O8-tuJlUzwhjgMz2JgposKHzL8\",\n" +
                "      \"f\":\"eyJraWQiOiJjN2Y2MDM2OS04MDMyLTQ1MWEtODcxZC1iNDhkMzA0YTJiMzUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJhY3IiOiIwIiwibGVnYWN5X3VzZXJfaWQiOiIxMjAwNzEwNyIsImFtciI6WyJja2UiXSwiYXV0aF90aW1lIjoxNzEyNTYyMDE5LCJpc3MiOiJodHRwczpcL1wvaWRlbnRpdHktcHJlLnNjaGlic3RlZC5jb21cLyIsImV4cCI6MTcxMjU2NTYxOSwiaWF0IjoxNzEyNTYyMDE5LCJub25jZSI6IlBCNXVyME1HbG4ifQ.m_cZBMtj7SlXmaAfVZNWK_wv8WQufpVRUX6a8pNtBppVZ8sQ0J0KswWjIC7uPsPoaP6jBQTfyy7w3JL9SCs00DK3AAX7spYDssGLac-YO_5sEzeX4bGsPwY5xH5oRgL1JspCTSxPqk-oArduzZYffHVq4g50_MweU6vNZ_6qRtFaDQ8DzTtM6qS3lr03zy1ckptn3eemL5PbZix-ZSVinKFIOsTeQaS9b7xVMbgupP6FRtRTuwU2248OW2b_2lxWlv2mX51tiFy1oyYcbbOu_TY94p0QHDrD85CclpMwDBNG4bc2rJ9hBcm4QiLLi6TMdeYdb-7KfTdvH6_xWVxdLQ\",\n" +
                "      \"g\":{\n" +
                "         \"d\":\"https://identity-pre.schibsted.com/\",\n" +
                "         \"e\":\"bcd62f69-aaeb-5179-b81a-3e245cd779af\",\n" +
                "         \"f\":\"12007107\",\n" +
                "         \"g\":[\n" +
                "            \"602525f2b41fa31789a95aa8\"\n" +
                "         ],\n" +
                "         \"h\":1712565619,\n" +
                "         \"i\":\"PB5ur0MGln\",\n" +
                "         \"j\":[\n" +
                "            \"cke\"\n" +
                "         ]\n" +
                "      }\n" +
                "   },\n" +
                "   \"c\":\"04 08, 2024 09:40:19\"\n" +
                "}"

        private const val REFRESH_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyNzM4NjE3LCJ0eXBlIjoicmVmcmVzaCIsImlhdCI6MTcxMjEzMzgxNywiY2xpZW50X2lkIjoiNjAyNTI1ZjJiNDFmYTMxNzg5YTk1YWE4IiwianRpIjoiYWNkNWNkNmUtMTNkNS00NTYxLTg5NjYtMzhlYTY3NjNmNGQ3Iiwic2lkIjoiYnA4bWsifQ.N1nyl_9C0-ClPb_eSytXxi_cOrRVZEWjYKU6kz8klVU"
        private const val ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJ1c2VyX2lkIjoiMTIwMDcxMDciLCJzY29wZSI6Im9wZW5pZCBvZmZsaW5lX2FjY2VzcyIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS1wcmUuc2NoaWJzdGVkLmNvbVwvIiwiZXhwIjoxNzEyMTMzODc3LCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzEyMTMzODE3LCJjbGllbnRfaWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJqdGkiOiJlZDQzYTMwNi1mOTBkLTRjYmUtYTU1ZC1lMzM3ZjM3MzhlZjEifQ.sckisjhfp-BoB379HI2cnPeDEH1XJMBghcBBRtDC4vY"
        private const val ID_TOKEN = "eyJraWQiOiJjN2Y2MDM2OS04MDMyLTQ1MWEtODcxZC1iNDhkMzA0YTJiMzUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJiY2Q2MmY2OS1hYWViLTUxNzktYjgxYS0zZTI0NWNkNzc5YWYiLCJhdWQiOiI2MDI1MjVmMmI0MWZhMzE3ODlhOTVhYTgiLCJhY3IiOiIwIiwibGVnYWN5X3VzZXJfaWQiOiIxMjAwNzEwNyIsImFtciI6WyJwd2QiXSwiYXV0aF90aW1lIjoxNzEyMTMzODA4LCJpc3MiOiJodHRwczpcL1wvaWRlbnRpdHktcHJlLnNjaGlic3RlZC5jb21cLyIsImV4cCI6MTcxMjEzNzQxNywiaWF0IjoxNzEyMTMzODE3LCJub25jZSI6IjRlTEhtWG81RVQifQ.NXIQw4lWp3FXc8MzQNmE5frV-KsmOG9JeKQBt9bCYcrBAHMvhw2bQQwm9bpjM-y36GGxtFanZGz6hyitZl_YGwU_FuM2XXWG1r5L1J2v2rFCfMnZdYfj-to28lK4JiYDU2-rc_eywdbzSbmQtps7qRxHWYTjf3gbkU5kEguYNMopncpk77pzRT3jjaBuzIPGoLBjLFG54FTKCFeeVZf-H8lSEz8-8x-p7WczLjroDHrOYdGznm9MytllN5sO1hR5d4_j6AiWfV_cyo2DadCoVcyeuI7lOoZMsP5B_HTiemj6c_C5Zl6ePKoc_8dvKGnmG6ArbQNdx1bcUbZ5m5KHwg"
    }
}