package com.schibsted.account

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent;
import android.widget.Button
import com.schibsted.account.android.webflows.Client
import com.schibsted.account.android.webflows.client.Environment

class MainActivity : AppCompatActivity() {
    private val client: Client = Client(Environment.PRE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val button = findViewById<Button>(R.id.loginButton)
        button.setOnClickListener({
            val loginUrl = client.generateLoginUrl("5fc8feb4653bd9707bbd40e9", "com.sdk-example.pre.5fc8feb4653bd9707bbd40e9://login", null, setOf<String>())
            println(loginUrl)

            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(this, Uri.parse(loginUrl))
        })
    }
}