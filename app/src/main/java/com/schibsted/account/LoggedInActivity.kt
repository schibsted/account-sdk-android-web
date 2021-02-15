package com.schibsted.account

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.schibsted.account.android.webflows.client.Client

class LoggedInActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logged_in)

        val client = Client(applicationContext, MainActivity.clientConfig)

        val authResponse = intent?.data?.query ?: return
        Log.i(LOG_TAG, "Auth response: $authResponse")

        client.handleAuthenticationResponse(authResponse) {
            Log.i(LOG_TAG, "Login complete")
        }
    }

    companion object {
        const val LOG_TAG = "LoggedInActivity"
    }
}
