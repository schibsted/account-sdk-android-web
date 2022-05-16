package com.schibsted.account.example

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import com.schibsted.account.BuildConfig
import com.schibsted.account.example.ClientConfig.environment
import com.schibsted.account.example.HttpClient.instance
import com.schibsted.account.example.MainActivity.Companion.LOGIN_FAILED_EXTRA
import com.schibsted.account.webflows.activities.AuthorizationManagementActivity
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.client.ClientConfiguration
import timber.log.Timber

class ExampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        initClient()
        initManualClient()
        initAuthorizationManagement()
        initTimber()
    }

    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun initClient() {
        val clientConfig = ClientConfiguration(
            env = environment,
            clientId = ClientConfig.clientId,
            redirectUri = ClientConfig.loginRedirectUri
        )
        client = Client(
            context = applicationContext,
            configuration = clientConfig,
            httpClient = instance
        )
    }

    private fun initManualClient() {
        val clientConfig = ClientConfiguration(
            env = environment,
            clientId = ClientConfig.clientId,
            redirectUri = ClientConfig.manualLoginRedirectUri
        )
        manualClient = Client(
            context = applicationContext,
            configuration = clientConfig,
            httpClient = instance,
            logoutCallback = {
                Timber.i("Received a logout event from client")
            })
    }

    private fun initAuthorizationManagement() {
        val completionIntent = Intent(this, MainActivity::class.java)
        val cancelIntent = Intent(this, MainActivity::class.java)
        cancelIntent.putExtra(LOGIN_FAILED_EXTRA, true)
        cancelIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        AuthorizationManagementActivity.setup(
            client = client,
            completionIntent = PendingIntent.getActivity(
                this,
                0,
                completionIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            ),
            cancelIntent = PendingIntent.getActivity(
                this,
                1,
                cancelIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
        )
    }

    companion object {
        lateinit var client: Client

        lateinit var manualClient: Client
    }
}
