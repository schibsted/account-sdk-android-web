package com.schibsted.account.webflows.activities

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RedirectUriReceiverActivityTest {
    @Test
    fun testForwardsRedirectToAuthorizationManagementActivity() {
        val redirectUri: Uri = Uri.parse("https://client.example.com/redirect")
        val redirectIntent =
            Intent(getApplicationContext(), RedirectUriReceiverActivity::class.java).apply {
                data = redirectUri
            }

        intending(
            Matchers.allOf(
                IntentMatchers.hasComponent(AuthorizationManagementActivity::class.java.name),
                IntentMatchers.hasData(redirectUri)
            )
        ).respondWith(ActivityResult(Activity.RESULT_OK, Intent()))

        val scenario = launch<RedirectUriReceiverActivity>(redirectIntent)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Before
    fun setup() {
        Intents.init()
    }

    @After
    fun teardown() {
        Intents.release()
    }
}
