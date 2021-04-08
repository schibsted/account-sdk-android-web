package com.schibsted.account.example;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.schibsted.account.R;
import com.schibsted.account.android.webflows.user.User;
import com.schibsted.account.android.webflows.user.UserSession;

import static com.schibsted.account.example.KotlinLambdaCompat.wrap;

public class LoggedInActivity extends AppCompatActivity {
    public static String USER_SESSION_EXTRA = "com.schibsted.account.USER_SESSION";
    private static String LOG_TAG = "LoggedInActivity";

    @Nullable
    private User user = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logged_in);

        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            if (user != null) {
                user.logout();
            }
            finish();
        });

        Button profileDataButton = findViewById(R.id.profileDataButton);
        profileDataButton.setOnClickListener(v -> {
            if (user != null) {
                user.fetchProfileData(wrap(result -> result
                                .onSuccess(wrap(value -> Log.i(LOG_TAG, "Profile data " + value)))
                                .onFailure(wrap(error -> Log.i(LOG_TAG, "Failed to fetch profile data " + error)))
                        )
                );
            }
        });

        Button sessionExchangeButton = findViewById(R.id.sessionExchangeButton);
        sessionExchangeButton.setOnClickListener(v -> {
            if (user != null) {
                user.webSessionUrl(ClientConfig.webClientId,
                        ClientConfig.webClientRedirectUri,
                        wrap(result -> result
                                .onSuccess(wrap(value -> Log.i(LOG_TAG, "Session exchange URL: " + value)))
                                .onFailure(wrap(error -> Log.i(LOG_TAG, "Failed to start session exchange " + error)))
                        )
                );
            }
        });

        UserSession userSession = getIntent().getParcelableExtra(USER_SESSION_EXTRA);
        if (userSession != null) {
            updateUser(new User(ExampleApp.client, userSession));
        } else {
            updateUser(null);
        }
    }

    public static Intent intentWithUser(Context context, User user) {
        Intent intent = new Intent(context, LoggedInActivity.class);
        intent.putExtra(USER_SESSION_EXTRA, user.getSession());
        return intent;
    }

    private void updateUser(User user) {
        this.user = user;
        if (user == null) {
            TextView t = findViewById(R.id.loggedInText);
            t.setText("Not logged-in");
        }
    }
}
