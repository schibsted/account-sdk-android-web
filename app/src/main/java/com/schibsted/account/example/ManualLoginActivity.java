package com.schibsted.account.example;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.schibsted.account.R;
import com.schibsted.account.android.webflows.client.Client;
import com.schibsted.account.android.webflows.client.ClientConfiguration;
import com.schibsted.account.android.webflows.user.User;

import static com.schibsted.account.example.KotlinLambdaCompat.wrap;

public class ManualLoginActivity extends AppCompatActivity {
    private static String LOG_TAG = "ManualLoginActivity";

    private Client client;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_login);

        ClientConfiguration clientConfig = new ClientConfiguration(
                ClientConfig.getEnvironment(),
                ClientConfig.clientId,
                ClientConfig.manualLoginRedirectUri
        );
        client = new Client(this, clientConfig, HttpClient.getInstance());

        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setOnClickListener(v -> client.launchAuth(this));

        Button resumeButton = findViewById(R.id.resumeButton);
        resumeButton.setOnClickListener(v -> {
            User user = ExampleApp.client.resumeLastLoggedInUser();
            if (user != null) {
                startLoggedInActivity(user);
            } else {
                Log.i(LOG_TAG, "User could not be resumed");
            }
        });

        if (getIntent().getData() != null) {
            client.handleAuthenticationResponse(getIntent(), wrap(result -> {
                Log.i(LOG_TAG, "Login complete");
                result
                        .foreach(wrap(this::startLoggedInActivity))
                        .left().foreach(wrap(error -> {
                                Log.i(LOG_TAG, "Something went wrong: " + error);
                                Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
                        }));
            }));
        }
    }

    private void startLoggedInActivity(User user) {
        startActivity(LoggedInActivity.intentWithUser(this, user));
    }
}
