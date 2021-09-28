package com.schibsted.account.example

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.schibsted.account.R
import com.schibsted.account.databinding.ActivityManualLoginBinding
import com.schibsted.account.example.ClientConfig.environment
import com.schibsted.account.example.HttpClient.instance
import com.schibsted.account.example.LoggedInActivity.Companion.intentWithUser
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.client.ClientConfiguration
import com.schibsted.account.webflows.client.LoginError
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Either
import timber.log.Timber

class ManualLoginActivity : AppCompatActivity() {

    private var _binding: ActivityManualLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var client: Client

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = DataBindingUtil.setContentView(this, R.layout.activity_manual_login)

        initClient()
        initLoginButton()
        initResumeButton()

        if (intent.data != null) {
            handleAuthenticationResponse()
        }


    }

    private fun handleAuthenticationResponse() {
        client.handleAuthenticationResponse(intent) { result: Either<LoginError?, User> ->
            Timber.i("Login complete")
            result
                .foreach { user: User -> startLoggedInActivity(user) }
                .left()
                .foreach { error: LoginError? ->
                    Timber.i("Something went wrong: $error")
                    Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun startLoggedInActivity(user: User) {
        startActivity(intentWithUser(this, user))
    }

    private fun initClient() {
        val clientConfig = ClientConfiguration(
            environment,
            ClientConfig.clientId,
            ClientConfig.manualLoginRedirectUri
        )
        client = Client(this, clientConfig, instance)
    }

    private fun initLoginButton() {
        binding.loginButton.setOnClickListener {
            client.launchAuth(this)
        }
    }

    private fun initResumeButton() {
        binding.resumeButton.setOnClickListener {
            ExampleApp.client.resumeLastLoggedInUser { user ->
                if (user != null) {
                    startLoggedInActivity(user)
                } else {
                    Toast.makeText(this, "User could not be resumed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
