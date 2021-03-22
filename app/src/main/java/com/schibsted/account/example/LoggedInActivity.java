package com.schibsted.account.example;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.schibsted.account.R;
import com.schibsted.account.android.webflows.client.Client;
import com.schibsted.account.android.webflows.user.User;
import com.schibsted.account.android.webflows.user.UserSession;

import static com.schibsted.account.example.KotlinLambdaCompat.wrap;

public class LoggedInActivity extends AppCompatActivity {
    private static String LOG_TAG = "LoggedInActivityJava";

    @Nullable
    private User user = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logged_in);

        Client client = new Client(getApplicationContext(), ClientConfig.INSTANCE.getInstance(), HttpClient.INSTANCE.getInstance());

        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            if (user != null) {
                user.logout();
            }
            finish();
        });

        Button profileDataButton = findViewById(R.id.profileDataButton);
        profileDataButton.setOnClickListener(v -> {
            if (user != null)
                user.fetchProfileData(wrap(result -> result
                                .onSuccess(wrap(value -> Log.i(LOG_TAG, "Profile data " + value)))
                                .onFailure(wrap(error -> Log.i(LOG_TAG, "Failed to fetch profile data " + error)))
                        )
                );
        });
        UserSession userSession = getIntent().getParcelableExtra(MainActivity.USER_SESSION_EXTRA);
        if (userSession != null) {
            updateUser(new User(client, userSession));
        } else {
            handleAuthenticationResponse(client);
        }
    }

    private void handleAuthenticationResponse(Client client) {
        String authResponse = getIntent().getData().getQuery();
        Log.i(LOG_TAG, "Auth response: $authResponse");

        client.handleAuthenticationResponse(authResponse, wrap(result -> {
            Log.i(LOG_TAG, "Login complete");
            result
                    .onSuccess(wrap(this::updateUser))
                    .onFailure(wrap(error -> Log.i(LOG_TAG, "Something went wrong: " + error)));
        }));
    }

    private void updateUser(User user) {
        this.user = user;
    }
}
