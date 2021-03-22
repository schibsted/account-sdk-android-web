package com.schibsted.account.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.schibsted.account.R
import com.schibsted.account.android.webflows.client.Client

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val client = Client(applicationContext, ClientConfig.instance, HttpClient.instance)

        val loginButton = findViewById<Button>(R.id.loginButton)
        loginButton.setOnClickListener {
            val loginUrl = client.generateLoginUrl()
            Log.i(LOG_TAG, "Login url: $loginUrl")

            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(this, Uri.parse(loginUrl))
        }

        val resumeButton = findViewById<Button>(R.id.resumeButton)
        resumeButton.setOnClickListener {
            val user = client.resumeLastLoggedInUser()
            if (user != null) {
                val intent = Intent(this, LoggedInActivity::class.java).apply {
                    putExtra(USER_SESSION_EXTRA, user.session)
                }
                startActivity(intent)
            } else {
                Log.i(LOG_TAG, "User could not be resumed")
            }
        }
    }

    companion object {
        const val LOG_TAG = "MainActivity"

        const val USER_SESSION_EXTRA = "com.schibsted.account.USER_SESSION"
    }
}
