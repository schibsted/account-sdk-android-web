package com.schibsted.account.example;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import com.schibsted.account.R;
import com.schibsted.account.android.webflows.client.Client;
import com.schibsted.account.android.webflows.user.User;

public class MainActivityJava extends AppCompatActivity {
    public static String USER_SESSION_EXTRA = "com.schibsted.account.USER_SESSION";
    private static String LOG_TAG = "MainActivityJava";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Client client = new Client(getApplicationContext(), ClientConfig.INSTANCE.getInstance(), HttpClient.INSTANCE.getInstance());

        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setOnClickListener(v -> {
            String loginUrl = client.generateLoginUrl();
            Log.i(LOG_TAG, "Login url: $loginUrl");

            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
            customTabsIntent.launchUrl(this, Uri.parse(loginUrl));
        });

        Button resumeButton = findViewById(R.id.resumeButton);
        resumeButton.setOnClickListener(v -> {
            User user = client.resumeLastLoggedInUser();
            if (user != null) {
                Intent intent = new Intent(this, LoggedInActivity.class);
                intent.putExtra(USER_SESSION_EXTRA, user.getSession());
                startActivity(intent);
            } else {
                Log.i(LOG_TAG, "User could not be resumed");
            }
        });
    }
}
