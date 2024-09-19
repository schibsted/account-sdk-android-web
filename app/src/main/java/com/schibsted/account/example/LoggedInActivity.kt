package com.schibsted.account.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.schibsted.account.R
import com.schibsted.account.databinding.ActivityLoggedInBinding
import com.schibsted.account.webflows.api.HttpError
import com.schibsted.account.webflows.api.UserProfileResponse
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.user.UserSession
import com.schibsted.account.webflows.util.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.await
import timber.log.Timber
import java.net.URL

class LoggedInActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoggedInBinding

    private var user: User? = null
    private var client: Client? = null

    private val isUserLoggedIn: Boolean
        get() = user?.isLoggedIn() == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoggedInBinding.inflate(layoutInflater)

        setContentView(binding.root)

        evaluateAndUpdateUserSession()

        initLogoutButton()
        initProfileDataButton()
        initExternalIdButton()
        initGetCustomStateButton()
        initSessionExchangeButtonButton()
        initAccountPagesButtonButton()
        initMakeAuthenticatedRequestButton()
    }

    private fun initLogoutButton() {
        binding.logoutButton.setOnClickListener {
            if (isUserLoggedIn) {
                user?.logout()
            }
            finish()
        }
    }

    private fun initProfileDataButton() {
        binding.profileDataButton.setOnClickListener {
            if (isUserLoggedIn) {
                user?.fetchProfileData { result: Either<HttpError?, UserProfileResponse> ->
                    result
                        .onSuccess { value: UserProfileResponse ->
                            Timber.i("Profile data $value")
                        }
                        .onFailure { error: HttpError? ->
                            Timber.i("Failed to fetch profile data $error")
                        }
                }
            }
        }
    }

    private fun initExternalIdButton() {
        binding.externalIdButton.setOnClickListener {
            if (isUserLoggedIn) {
                Timber.i("ExternalId ${client?.getExternalId("pairId", "externalParty")} ")
            }
        }
    }

    private fun initGetCustomStateButton() {
        binding.customStateButton.setOnClickListener {
            if (isUserLoggedIn) {
                Timber.i("Custom state ${client?.getState()} ")
            }
        }
    }

    private fun initSessionExchangeButtonButton() {
        binding.sessionExchangeButton.setOnClickListener {
            if (isUserLoggedIn) {
                user?.webSessionUrl(
                    clientId = ClientConfig.WEB_CLIENT_ID,
                    redirectUri = ClientConfig.WEB_CLIENT_REDIRECT_URI,
                ) { result: Either<HttpError?, URL> ->
                    result
                        .onSuccess { value: URL ->
                            Timber.i("Session exchange URL: $value")
                        }
                        .onFailure { error: HttpError? ->
                            Timber.i("Failed to start session exchange $error")
                        }
                }
            }
        }
    }

    private fun initAccountPagesButtonButton() {
        binding.accountPagesButton.setOnClickListener {
            if (isUserLoggedIn) {
                CustomTabsIntent.Builder()
                    .build()
                    .launchUrl(this, Uri.parse(user?.accountPagesUrl().toString()))
            }
        }
    }

    private fun evaluateAndUpdateUserSession() {
        client =
            when (intent.getSerializableExtra(FLOW_EXTRA)) {
                Flow.AUTOMATIC -> ExampleApp.client
                Flow.MANUAL -> ExampleApp.manualClient
                else -> throw RuntimeException("Must provide a flow enum")
            }

        val userSession: UserSession? = intent.getParcelableExtra(USER_SESSION_EXTRA)
        client?.let { client ->
            val user = if (userSession != null) User(client, userSession) else null
            updateUser(user)
        }
    }

    private fun initMakeAuthenticatedRequestButton() {
        binding.testRetrofitAuthenticatedRequest.setOnClickListener {
            val myService: SimpleService =
                HttpClient.instance.newBuilder().let {
                    user?.bind(it)
                    Retrofit.Builder()
                        .baseUrl(ClientConfig.environment.url)
                        .client(it.build())
                        .build()
                        .create(SimpleService::class.java)
                }

            CoroutineScope(Dispatchers.IO).launch {
                val profileData = myService.userProfile(user?.userId.toString()).await()
                Timber.i("Authenticated request profile data: $profileData")
            }
        }
    }

    private fun updateUser(user: User?) {
        this.user = user
        if (user == null) {
            binding.loggedInText.text = getString(R.string.not_logged_in_text)
        }
    }

    companion object {
        private const val USER_SESSION_EXTRA = "com.schibsted.account.USER_SESSION"
        private const val FLOW_EXTRA = "com.schibsted.account.FLOW"

        enum class Flow {
            AUTOMATIC,
            MANUAL,
        }

        fun intentWithUser(
            context: Context?,
            user: User,
            flow: Flow,
        ): Intent {
            val intent = Intent(context, LoggedInActivity::class.java)
            intent.putExtra(USER_SESSION_EXTRA, user.session)
            intent.putExtra(FLOW_EXTRA, flow)
            return intent
        }
    }
}
