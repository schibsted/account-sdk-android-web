package com.schibsted.account.example;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import com.schibsted.account.R;
import com.schibsted.account.android.webflows.activities.AuthResultLiveData;
import com.schibsted.account.android.webflows.activities.NotAuthed;
import com.schibsted.account.android.webflows.user.User;
import com.schibsted.account.android.webflows.util.ResultOrError;

import static com.schibsted.account.example.KotlinLambdaCompat.wrap;

public class MainActivity extends AppCompatActivity {
    public static String LOGIN_FAILED_EXTRA = "com.schibsted.account.LOGIN_FAILED";
    private static String LOG_TAG = "MainActivity";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setOnClickListener(v -> {
            Intent authIntent = ExampleApp.client.getAuthenticationIntent(this);
            startActivity(authIntent);
        });

        Button manualLoginButton = findViewById(R.id.manualLoginButton);
        manualLoginButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ManualLoginActivity.class);
            startActivity(intent);
        });

        if (getIntent().getBooleanExtra(LOGIN_FAILED_EXTRA, false)) {
            Log.i(LOG_TAG, "MainActivity started after user canceled login");
            Toast.makeText(this, "Login cancelled", Toast.LENGTH_SHORT).show();
        }

        AuthResultLiveData.get().observe(this, (Observer<ResultOrError<? extends User, ? extends NotAuthed>>) result -> {
            result
                    .onSuccess(wrap(this::startLoggedInActivity))
                    .onFailure(wrap(error -> {
                        if (error == NotAuthed.NoLoggedInUser.INSTANCE) {
                            // no logged-in user could be resumed or user was logged-out
                            Log.i(LOG_TAG, "No logged-in user");
                            Toast.makeText(this, "No logged-in user", Toast.LENGTH_SHORT).show();
                        } else if (error == NotAuthed.CancelledByUser.INSTANCE) {
                            Log.i(LOG_TAG, "Login cancelled");
                            Toast.makeText(this, "Login cancelled", Toast.LENGTH_SHORT).show();
                        } else if (error == NotAuthed.AuthInProgress.INSTANCE) {
                            Log.i(LOG_TAG, "Auth in progress");
                        } else {
                            Log.i(LOG_TAG, "Something went wrong: " + error);
                            Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
                        }
                    }));
        });
    }

    private void startLoggedInActivity(User user) {
        startActivity(LoggedInActivity.intentWithUser(this, user));
    }
}
