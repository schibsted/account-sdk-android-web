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
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.user.UserSession
import com.schibsted.account.webflows.util.Either
import timber.log.Timber
import java.net.URL

class LoggedInActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoggedInBinding

    private var user: User? = null

    private val isUserLoggedIn: Boolean
        get() = user?.isLoggedIn() == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoggedInBinding.inflate(layoutInflater)

        setContentView(binding.root)

        evaluateAndUpdateUserSession()

        initLogoutButton()
        initProfileDataButton()
        initSessionExchangeButtonButton()
        initAccountPagesButtonButton()

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
                        .foreach { value: UserProfileResponse ->
                            Timber.i("Profile data $value")
                        }
                        .left()
                        .foreach { error: HttpError? ->
                            Timber.i("Failed to fetch profile data $error")
                        }
                }
            }
        }
    }

    private fun initSessionExchangeButtonButton() {
        binding.sessionExchangeButton.setOnClickListener {
            if (isUserLoggedIn) {
                user?.webSessionUrl(
                    clientId = ClientConfig.webClientId,
                    redirectUri = ClientConfig.webClientRedirectUri,
                )
                { result: Either<HttpError?, URL> ->
                    result
                        .foreach { value: URL ->
                            Timber.i("Session exchange URL: $value")
                        }
                        .left()
                        .foreach { error: HttpError? ->
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
        val userSession: UserSession? = intent.getParcelableExtra(USER_SESSION_EXTRA)
        val user = if (userSession != null) User(ExampleApp.client, userSession) else null
        updateUser(user)
    }

    private fun updateUser(user: User?) {
        this.user = user
        if (user == null) {
            binding.loggedInText.text = getString(R.string.not_logged_in_text)
        }
    }

    companion object {
        var USER_SESSION_EXTRA = "com.schibsted.account.USER_SESSION"

        fun intentWithUser(context: Context?, user: User): Intent {
            val intent = Intent(context, LoggedInActivity::class.java)
            intent.putExtra(USER_SESSION_EXTRA, user.session)
            return intent
        }
    }
}
