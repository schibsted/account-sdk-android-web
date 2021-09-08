package com.schibsted.account.example;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import com.schibsted.account.R;
import com.schibsted.account.webflows.user.User;
import com.schibsted.account.webflows.user.UserSession;

import static com.schibsted.account.example.KotlinLambdaCompat.wrap;

public class LoggedInActivity extends AppCompatActivity {
    public static String USER_SESSION_EXTRA = "com.schibsted.account.USER_SESSION";
    private static final String LOG_TAG = "LoggedInActivity";

    @Nullable
    private User user = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logged_in);

        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            if (user != null && user.isLoggedIn()) {
                user.logout();
            }
            finish();
        });

        Button profileDataButton = findViewById(R.id.profileDataButton);
        profileDataButton.setOnClickListener(v -> {
            if (user != null && user.isLoggedIn()) {
                user.fetchProfileData(wrap(result -> result
                                .foreach(wrap(value -> Log.i(LOG_TAG, "Profile data " + value)))
                                .left().foreach(wrap(error -> Log.i(LOG_TAG, "Failed to fetch profile data " + error)))
                        )
                );
            }
        });

        Button sessionExchangeButton = findViewById(R.id.sessionExchangeButton);
        sessionExchangeButton.setOnClickListener(v -> {
            if (user != null && user.isLoggedIn()) {
                user.webSessionUrl(ClientConfig.webClientId,
                        ClientConfig.webClientRedirectUri,
                        wrap(result -> result
                                .foreach(wrap(value -> Log.i(LOG_TAG, "Session exchange URL: " + value)))
                                .left().foreach(wrap(error -> Log.i(LOG_TAG, "Failed to start session exchange " + error)))
                        )
                );
            }
        });

        Button accountPagesButton = findViewById(R.id.accountPagesButton);
        accountPagesButton.setOnClickListener(v -> {
            if (user != null && user.isLoggedIn()) {
                new CustomTabsIntent.Builder()
                        .build()
                        .launchUrl(this, Uri.parse(user.accountPagesUrl().toString()));
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
