package com.schibsted.account.webflows.activities

import android.app.Activity
import android.app.Instrumentation
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.annotation.UiThreadTest
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.schibsted.account.webflows.testsupport.TestActivity
import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.testutil.assertLeft
import com.schibsted.account.testutil.assertRight
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.client.LoginResultHandler
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Either.Right
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class AuthorizationManagementActivityTest {
    private val testActivityIntent = Intent(
        getApplicationContext(),
        TestActivity::class.java
    )

    @Before
    @UiThreadTest
    fun setup() {
        Intents.init()
        setupAuthorizationManagementActivity(mockk(relaxed = true))
    }

    @After
    fun teardown() {
        Intents.release()
        AuthResultLiveDataTest.resetInstance()
    }

    private fun setupAuthorizationManagementActivity(client: Client) {
        val completionIntent =
            spyk(PendingIntent.getActivity(getApplicationContext(), 0, testActivityIntent, 0))
        val cancelIntent =
            spyk(PendingIntent.getActivity(getApplicationContext(), 1, testActivityIntent, 0))
        AuthorizationManagementActivity.setup(client, completionIntent, cancelIntent)
    }

    @Test
    fun testShouldStartAuthIfNotStarted() {
        val authIntent = Intent().apply {
            putExtra("AUTH", true)
        }
        val intent =
            AuthorizationManagementActivity.createStartIntent(getApplicationContext(), authIntent)

        Intents.intending(IntentMatchers.hasExtras(Matchers.equalTo(authIntent.extras)))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()))
        launch<AuthorizationManagementActivity>(intent)
        AuthResultLiveData.get().value!!.assertLeft { assertEquals(NotAuthed.AuthInProgress, it) }
    }

    @Test
    fun testAuthResponseShouldSendCompletionIntent() {
        val authResponse: Uri =
            Uri.parse("https://client.example.com/redirect?code=12345&state=test")

        val ctx = getApplicationContext<Context>()
        val intent = Intent(ctx, AuthorizationManagementActivity::class.java).apply {
            data = authResponse
        }

        AuthResultLiveDataTest.resetInstance()
        val client = mockk<Client>(relaxed = true)
        val user = User(client, Fixtures.userTokens)
        every { client.handleAuthenticationResponse(any(), any()) } answers {
            val callback = secondArg<LoginResultHandler>()
            callback(Right(user))
        }
        setupAuthorizationManagementActivity(client)

        launch<AuthorizationManagementActivity>(intent)

        AuthResultLiveData.get().value!!.assertRight { assertEquals(user, it) }
        verify(exactly = 1) {
            client.handleAuthenticationResponse(withArg { intent ->
                assertEquals(authResponse, intent.data)
            }, any())
        }
        verify(exactly = 1) { AuthorizationManagementActivity.completionIntent.send() }
        verify(exactly = 0) { AuthorizationManagementActivity.cancelIntent.send() }
    }

    @Test
    fun testUserCancelActionResultingEmptyIntentShouldSendCancelIntent() {
        val ctx = getApplicationContext<Context>()
        val intent = Intent(ctx, AuthorizationManagementActivity::class.java)

        launch<AuthorizationManagementActivity>(intent)
        AuthResultLiveData.get().value!!.assertLeft { assertEquals(NotAuthed.CancelledByUser, it) }
        verify(exactly = 1) { AuthorizationManagementActivity.cancelIntent.send() }
        verify(exactly = 0) { AuthorizationManagementActivity.completionIntent.send() }
    }
}
