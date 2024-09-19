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
import com.schibsted.account.webflows.tracking.SchibstedAccountTrackerStore
import com.schibsted.account.webflows.tracking.SchibstedAccountTrackingEvent
import com.schibsted.account.webflows.tracking.SchibstedAccountTrackingListener
import timber.log.Timber

class ExampleApp : Application() {
    override fun onCreate() {
        super.onCreate()

        initClient()
        initManualClient()
        initAuthorizationManagement()
        initTimber()
        initTracking()
    }

    private fun initTracking() {
        val listener =
            object : SchibstedAccountTrackingListener {
                override fun onEvent(event: SchibstedAccountTrackingEvent) {
                    Timber.d("Tracked event ${event::class.simpleName}")
                }
            }

        SchibstedAccountTrackerStore.addTrackingListener(listener)
    }

    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun initClient() {
        val clientConfig =
            ClientConfiguration(
                env = environment,
                clientId = ClientConfig.CLIENT_ID,
                redirectUri = ClientConfig.LOGIN_REDIRECT_URI,
            )
        client =
            Client(
                context = applicationContext,
                configuration = clientConfig,
                httpClient = instance,
            )
    }

    private fun initManualClient() {
        val clientConfig =
            ClientConfiguration(
                env = environment,
                clientId = ClientConfig.CLIENT_ID,
                redirectUri = ClientConfig.MANUAL_LOGIN_REDIRECT_URI,
            )
        manualClient =
            Client(
                context = applicationContext,
                configuration = clientConfig,
                httpClient = instance,
                logoutCallback = {
                    Timber.i("Received a logout event from client")
                },
            )
    }

    private fun initAuthorizationManagement() {
        val completionIntent = Intent(this, MainActivity::class.java)
        val cancelIntent = Intent(this, MainActivity::class.java)
        cancelIntent.putExtra(LOGIN_FAILED_EXTRA, true)
        cancelIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        AuthorizationManagementActivity.setup(
            client = client,
            completionIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    completionIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0,
                ),
            cancelIntent =
                PendingIntent.getActivity(
                    this,
                    1,
                    cancelIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0,
                ),
        )
    }

    companion object {
        lateinit var client: Client

        lateinit var manualClient: Client
    }
}
