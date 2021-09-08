package com.schibsted.account.example;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;

import com.schibsted.account.webflows.activities.AuthorizationManagementActivity;
import com.schibsted.account.webflows.client.Client;
import com.schibsted.account.webflows.client.ClientConfiguration;

public class ExampleApp extends Application {
    public static Client client;

    @Override
    public void onCreate() {
        super.onCreate();

        ClientConfiguration clientConfig = new ClientConfiguration(ClientConfig.getEnvironment(), ClientConfig.clientId, ClientConfig.loginRedirectUri);
        client = new Client(getApplicationContext(), clientConfig, HttpClient.getInstance());

        Intent completionIntent = new Intent(this, MainActivity.class);
        Intent cancelIntent = new Intent(this, MainActivity.class);
        cancelIntent.putExtra(MainActivity.Companion.getLOGIN_FAILED_EXTRA(), true);
        cancelIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        AuthorizationManagementActivity.setup(
                client,
                PendingIntent.getActivity(this, 0, completionIntent, 0),
                PendingIntent.getActivity(this, 1, cancelIntent, 0)
        );
    }
}
