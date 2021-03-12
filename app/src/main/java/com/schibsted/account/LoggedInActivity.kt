package com.schibsted.account

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.schibsted.account.android.webflows.client.Client
import com.schibsted.account.android.webflows.user.User
import com.schibsted.account.android.webflows.user.UserSession
import com.schibsted.account.android.webflows.util.ResultOrError

class LoggedInActivity : AppCompatActivity() {
    private var user: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logged_in)

        val client = Client(applicationContext, MainActivity.clientConfig)

        val logoutButton = findViewById<Button>(R.id.logoutButton)
        logoutButton.setOnClickListener {
            user?.logout()
            finish()
        }

        val userSession = intent.getParcelableExtra<UserSession>(MainActivity.USER_SESSION_EXTRA)
        if (userSession != null) {
            updateUser(User(client, userSession))
        } else {
            handleAuthenticationResponse(client)
        }
    }

    private fun handleAuthenticationResponse(client: Client) {
        val authResponse = intent?.data?.query ?: return
        Log.i(LOG_TAG, "Auth response: $authResponse")

        client.handleAuthenticationResponse(authResponse) { result ->
            Log.i(LOG_TAG, "Login complete")
            when (result) {
                is ResultOrError.Success -> updateUser(result.value)
                is ResultOrError.Failure -> Log.i(
                    LOG_TAG,
                    "Something went wrong: ${result.error}"
                )
            }
        }
    }

    private fun updateUser(user: User) {
        Log.i(LOG_TAG, user.toString())
        this.user = user
    }

    companion object {
        const val LOG_TAG = "LoggedInActivity"
    }
}
