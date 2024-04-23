package com.schibsted.account.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.schibsted.account.databinding.ActivityMainBinding
import com.schibsted.account.webflows.activities.AuthResultLiveData
import com.schibsted.account.webflows.activities.NotAuthed
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Either
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.getBooleanExtra(LOGIN_FAILED_EXTRA, false)) {
            Timber.i("MainActivity started after user canceled login")
            Toast.makeText(this, "Login cancelled", Toast.LENGTH_SHORT).show()
        }

        initializeButtons()
        observeAuthResultLiveData()
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            ExampleApp.client.requestLoginPrompt(applicationContext, supportFragmentManager, true)
        }
    }


    private fun initializeButtons() {
        binding.loginButton.setOnClickListener {
            startActivity(ExampleApp.client.getAuthenticationIntent(this, "customState"))
        }
        binding.manualLoginButton.setOnClickListener {
            startActivity(Intent(this, ManualLoginActivity::class.java))
        }
    }

    private fun observeAuthResultLiveData() {
        AuthResultLiveData.get().observe(this, Observer { result: Either<NotAuthed, User> ->
            result
                .onSuccess { user: User -> startLoggedInActivity(user) }
                .onFailure { state: NotAuthed ->
                    handleNotAuthedState(state)
                }
        } as Observer<Either<NotAuthed, User>>)
    }

    private fun handleNotAuthedState(state: NotAuthed) {
        when (state) {
            NotAuthed.NoLoggedInUser -> {
                // no logged-in user could be resumed or user was logged-out
                Timber.i("No logged-in user")
                Toast.makeText(this, "No logged-in user", Toast.LENGTH_SHORT).show()
            }
            NotAuthed.CancelledByUser -> {
                Timber.i("Login cancelled")
                Toast.makeText(this, "Login cancelled", Toast.LENGTH_SHORT).show()
            }
            NotAuthed.AuthInProgress -> {
                Timber.i("Auth in progress")
            }
            else -> {
                Timber.i("Something went wrong: $state")
                Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startLoggedInActivity(user: User) {
        startActivity(
            LoggedInActivity.intentWithUser(
                this,
                user,
                LoggedInActivity.Companion.Flow.AUTOMATIC
            )
        )
    }

    companion object {
        var LOGIN_FAILED_EXTRA = "com.schibsted.account.LOGIN_FAILED"
    }
}
