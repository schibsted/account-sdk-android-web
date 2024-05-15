package com.schibsted.account.example

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.schibsted.account.databinding.ActivityManualLoginBinding
import com.schibsted.account.example.LoggedInActivity.Companion.intentWithUser
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.client.LoginError
import com.schibsted.account.webflows.persistence.StorageError
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Either
import timber.log.Timber

class ManualLoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityManualLoginBinding

    private lateinit var client: Client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityManualLoginBinding.inflate(layoutInflater)

        setContentView(binding.root)

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
                .onSuccess { user: User -> startLoggedInActivity(user) }
                .onFailure { error: LoginError? ->
                    Timber.i("Something went wrong: $error")
                    Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun startLoggedInActivity(user: User) {
        startActivity(intentWithUser(this, user, LoggedInActivity.Companion.Flow.MANUAL))
    }

    private fun initClient() {
        client = ExampleApp.manualClient
    }

    private fun initLoginButton() {
        binding.loginButton.setOnClickListener {
            client.launchAuth(this, "customState")
        }
    }

    private fun initResumeButton() {
        binding.resumeButton.setOnClickListener {
            client.resumeLastLoggedInUser { result ->
                result
                    .onSuccess { user ->
                        if (user != null) {
                            startLoggedInActivity(user)
                        } else {
                            Toast.makeText(this, "User could not be resumed", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    .onFailure {
                        when (it) {
                            is StorageError.UnexpectedError ->
                                Toast.makeText(
                                    this,
                                    "User could not be resumed, error: ${it.cause.message} ",
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                        }
                    }
            }
        }
    }
}
