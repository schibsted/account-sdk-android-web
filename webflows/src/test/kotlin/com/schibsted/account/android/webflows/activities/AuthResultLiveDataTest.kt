package com.schibsted.account.android.webflows.activities

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.schibsted.account.android.testutil.Fixtures
import com.schibsted.account.android.testutil.assertError
import com.schibsted.account.android.testutil.assertSuccess
import com.schibsted.account.android.webflows.client.Client
import com.schibsted.account.android.webflows.client.LoginError
import com.schibsted.account.android.webflows.client.LoginResultHandler
import com.schibsted.account.android.webflows.user.User
import com.schibsted.account.android.webflows.util.ResultOrError
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthResultLiveDataTest {
    companion object {
        fun resetInstance() {
            // reset singleton instance
            val target = AuthResultLiveData::class.java
            val field = target.getDeclaredField("instance")
            with(field) {
                isAccessible = true
                set(target, null)
            }
        }
    }

    @After
    fun teardown() {
        resetInstance()
    }

    @Test
    fun getIfInitialisedReturnsNullIfNotInitialised() {
        assertNull(AuthResultLiveData.getIfInitialised())
    }

    @Test
    fun getIfInitialisedReturnsInstance() {
        AuthResultLiveData.create(mockk(relaxed = true))
        assertNotNull(AuthResultLiveData.getIfInitialised())
    }

    @Test
    fun initResumesLoggedInUser() {
        val client = mockk<Client>(relaxed = true)
        val user = User(client, Fixtures.userTokens)
        every { client.resumeLastLoggedInUser() } returns user

        AuthResultLiveData.create(client)
        AuthResultLiveData.get().value!!.assertSuccess { assertEquals(user, it) }
    }

    @Test
    fun initResumesNoLoggedInUser() {
        val client = mockk<Client>(relaxed = true)
        every { client.resumeLastLoggedInUser() } returns null

        AuthResultLiveData.create(client)
        AuthResultLiveData.get().value!!.assertError { assertEquals(NotAuthed.NoLoggedInUser, it) }
    }

    @Test
    fun logoutUpdatesState() {
        val client = mockk<Client>(relaxed = true)
        val user = User(client, Fixtures.userTokens)
        every { client.resumeLastLoggedInUser() } returns user
        AuthResultLiveData.create(client)

        AuthResultLiveData.get().logout()
        AuthResultLiveData.get().value!!.assertError { assertEquals(NotAuthed.NoLoggedInUser, it) }
    }

    @Test
    fun updateHandlesSuccessResult() {
        val client = mockk<Client>(relaxed = true)
        val user = User(client, Fixtures.userTokens)
        every { client.handleAuthenticationResponse(any(), any()) } answers {
            val callback = secondArg<LoginResultHandler>()
            callback(ResultOrError.Success(user))
        }
        AuthResultLiveData.create(client)

        AuthResultLiveData.get().update(Intent().apply {
            data = Uri.parse("https://client.example.com/redirect?code=12345&state=test")
        })
        AuthResultLiveData.get().value!!.assertSuccess { assertEquals(user, it) }
    }

    @Test
    fun updateHandlesFailureResult() {
        val errorResult = LoginError.UnexpectedError("Test error")
        val client = mockk<Client>(relaxed = true) {
            every { handleAuthenticationResponse(any(), any()) } answers {
                val callback = secondArg<LoginResultHandler>()
                callback(ResultOrError.Failure(errorResult))
            }
        }
        AuthResultLiveData.create(client)

        AuthResultLiveData.get().update(Intent().apply {
            data = Uri.parse("https://client.example.com/redirect?code=12345&state=test")
        })
        AuthResultLiveData.get().value!!.assertError {
            when (it) {
                is NotAuthed.LoginFailed -> assertEquals(errorResult, it.error)
                else -> fail("Unexpected error: $it")
            }
        }
    }

    @Test
    fun userLogoutUpdatesAuthResultLiveData() {
        val client = mockk<Client>(relaxed = true)
        val user = User(client, Fixtures.userTokens)
        every { client.resumeLastLoggedInUser() } returns user
        AuthResultLiveData.create(client)

        AuthResultLiveData.get().value!!.assertSuccess { assertEquals(user, it) }
        user.logout()
        AuthResultLiveData.get().value!!.assertError {
            assertEquals(
                NotAuthed.NoLoggedInUser,
                it
            )
        }
    }
}
