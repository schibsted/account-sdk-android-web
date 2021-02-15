package com.schibsted.account

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.schibsted.account.android.webflows.client.Client
import com.schibsted.account.android.webflows.client.ClientConfiguration
import com.schibsted.account.android.webflows.client.Environment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val client = Client(applicationContext, clientConfig)

        val button = findViewById<Button>(R.id.loginButton)
        button.setOnClickListener {
            val loginUrl = client.generateLoginUrl()
            Log.i(LOG_TAG, "Login url: $loginUrl")

            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(this, Uri.parse(loginUrl))
        }
    }

    companion object {
        const val LOG_TAG = "MainActivity"
        val clientConfig = ClientConfiguration(
            Environment.PRE,
            "602525f2b41fa31789a95aa8",
            "com.sdk-example.pre.602525f2b41fa31789a95aa8://login"
        )
    }
}
