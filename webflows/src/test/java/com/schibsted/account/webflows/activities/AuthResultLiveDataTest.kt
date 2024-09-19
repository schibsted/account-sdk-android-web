package com.schibsted.account.webflows.activities

import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.testutil.assertLeft
import com.schibsted.account.testutil.assertRight
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.client.LoginError
import com.schibsted.account.webflows.client.LoginResultHandler
import com.schibsted.account.webflows.persistence.StorageError
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

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
    fun getIfNotInitialisedReturnsNewInstance() {
        assertNull(AuthResultLiveData.getIfInitialised())
        assertNotNull(AuthResultLiveData.get(mockk(relaxed = true)))
        assertNotNull(AuthResultLiveData.getIfInitialised())
    }

    @Test
    fun initResumesLoggedInUser() {
        val client = mockk<Client>(relaxed = true)
        val user = User(client, Fixtures.userTokens)
        every { client.resumeLastLoggedInUser(any()) } answers {
            val callback = firstArg<(Either<StorageError, User?>) -> Unit>()
            callback(Right(user))
        }

        AuthResultLiveData.create(client)
        AuthResultLiveData.get(client).value!!.assertRight { assertEquals(user, it) }
    }

    @Test
    fun initResumesNoLoggedInUser() {
        val client = mockk<Client>(relaxed = true)
        every { client.resumeLastLoggedInUser(any()) } answers {
            val callback = firstArg<(Either<StorageError, User?>) -> Unit>()
            callback(Right(null))
        }

        AuthResultLiveData.create(client)
        AuthResultLiveData.get(client).value!!.assertLeft { assertEquals(NotAuthed.NoLoggedInUser, it) }
    }

    @Test
    fun initHandlesStorageError() {
        val client = mockk<Client>(relaxed = true)
        every { client.resumeLastLoggedInUser(any()) } answers {
            val callback = firstArg<(Either<StorageError, User?>) -> Unit>()
            callback(Left(StorageError.UnexpectedError(Exception("Something went wrong"))))
        }

        AuthResultLiveData.create(client)
        AuthResultLiveData.get(client).value!!.assertLeft { assertEquals(NotAuthed.NoLoggedInUser, it) }
    }

    @Test
    fun updateHandlesSuccessResult() {
        val client = mockk<Client>(relaxed = true)
        val user = User(client, Fixtures.userTokens)
        every { client.handleAuthenticationResponse(any(), any()) } answers {
            val callback = secondArg<LoginResultHandler>()
            callback(Right(user))
        }
        AuthResultLiveData.create(client)

        AuthResultLiveData.get(client).update(
            Intent().apply {
                data = Uri.parse("https://client.example.com/redirect?code=12345&state=test")
            },
        )
        AuthResultLiveData.get(client).value!!.assertRight { assertEquals(user, it) }
    }

    @Test
    fun updateHandlesFailureResult() {
        val errorResult = LoginError.UnexpectedError("Test error")
        val client =
            mockk<Client>(relaxed = true) {
                every { handleAuthenticationResponse(any(), any()) } answers {
                    val callback = secondArg<LoginResultHandler>()
                    callback(Left(errorResult))
                }
            }
        AuthResultLiveData.create(client)

        AuthResultLiveData.get(client).update(
            Intent().apply {
                data = Uri.parse("https://client.example.com/redirect?code=12345&state=test")
            },
        )
        AuthResultLiveData.get(client).value!!.assertLeft {
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
        every { client.resumeLastLoggedInUser(any()) } answers {
            val callback = firstArg<(Either<StorageError, User?>) -> Unit>()
            callback(Right(user))
        }
        AuthResultLiveData.create(client)

        AuthResultLiveData.get(client).value!!.assertRight { assertEquals(user, it) }

        user.logout()
        shadowOf(Looper.getMainLooper()).idle()

        AuthResultLiveData.get(client).value!!.assertLeft {
            assertEquals(
                NotAuthed.NoLoggedInUser,
                it,
            )
        }
    }
}
