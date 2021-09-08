package com.schibsted.account.example

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import com.schibsted.account.example.ClientConfig.environment
import com.schibsted.account.example.HttpClient.instance
import com.schibsted.account.example.MainActivity.Companion.LOGIN_FAILED_EXTRA
import com.schibsted.account.webflows.activities.AuthorizationManagementActivity
import com.schibsted.account.webflows.client.ClientConfiguration
import com.schibsted.account.webflows.client.Client

class ExampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        initClient()
        initAuthorizationManagement()
    }

    private fun initClient() {
        val clientConfig =
            ClientConfiguration(environment, ClientConfig.clientId, ClientConfig.loginRedirectUri)
        client = Client(applicationContext, clientConfig, instance)
    }

    private fun initAuthorizationManagement() {
        val completionIntent = Intent(this, MainActivity::class.java)
        val cancelIntent = Intent(this, MainActivity::class.java)
        cancelIntent.putExtra(LOGIN_FAILED_EXTRA, true)
        cancelIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        AuthorizationManagementActivity.setup(
            client,
            PendingIntent.getActivity(this, 0, completionIntent, 0),
            PendingIntent.getActivity(this, 1, cancelIntent, 0)
        )
    }

    companion object {
        lateinit var client: Client
    }
}