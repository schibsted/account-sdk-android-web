package com.schibsted.account

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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
            val loginUrl = client.generateLoginUrl("clientID1", "redirectUri1", null, setOf<String>())
            println(loginUrl)
        })
    }
}