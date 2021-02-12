package com.schibsted.account

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent;
import android.widget.Button
import com.schibsted.account.android.webflows.client.Client
import com.schibsted.account.android.webflows.client.ClientConfiguration
import com.schibsted.account.android.webflows.client.Environment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val client = Client(
            applicationContext,
            ClientConfiguration(
                Environment.PRE,
                "602525f2b41fa31789a95aa8",
                "com.sdk-example.pre.602525f2b41fa31789a95aa8://login"
            )
        )

        val button = findViewById<Button>(R.id.loginButton)
        button.setOnClickListener({
            val loginUrl = client.generateLoginUrl()
            println(loginUrl)

            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(this, Uri.parse(loginUrl))
        })
    }
}