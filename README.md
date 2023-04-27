# Schibsted account Android SDK

![Build Status](https://github.com/schibsted/account-sdk-android-web/actions/workflows/ci.yaml/badge.svg)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/schibsted/account-sdk-android-web)
![Platform](https://img.shields.io/badge/Platform-Android%2021%2B-orange.svg?style=flat)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/schibsted/account-sdk-android-web/blob/master/LICENSE)

New implementation of the Schibsted account Android SDK using the web flows via
[Custom Tabs](https://developer.chrome.com/docs/android/custom-tabs/overview/):

* API documentation can be
found [here](https://pages.github.schibsted.io/spt-identity/account-sdk-android-web/).
* An example implementation of the SDK can be found
[here](https://github.schibsted.io/spt-identity/account-sdk-android-web/tree/master/app/src/main/java/com/schibsted/account/example).

## Getting started

To implement login with Schibsted account in your app, please first have a look at our
[getting started documentation](https://docs.schibsted.io/schibsted-account/gettingstarted/). This
will help you create a client and configure the necessary data.

### Important notes

* This SDK requires your client to be registered as a `public_mobile_client` in Self Service (see
  [getting started documentation](https://docs.schibsted.io/schibsted-account/gettingstarted/)
  for more information).
* Using [App Links](https://developer.android.com/training/app-links) should be preferred
  for [security reasons](https://tools.ietf.org/html/rfc8252#appendix-B.2).
  To support older Android versions, configure a fallback page at the same web address to forward
  authorization responses to your app via a custom scheme.

### Installation

The SDK is available via
[Maven Central](https://search.maven.org/artifact/com.schibsted.account/account-sdk-android-web):
```
implementation 'com.schibsted.account:account-sdk-android-web:<version>'
```

### Usage

1. In your `AndroidManifest.xml`, configure the necessary intent filter for
   `RedirectUriReceiverActivity` which handles the auth response after completed user
   creation/login.
   ```xml
   <activity
       android:name="com.schibsted.account.webflows.activities.RedirectUriReceiverActivity">
       <intent-filter>
           <action android:name="android.intent.action.VIEW" />
           <category android:name="android.intent.category.DEFAULT" />
           <category android:name="android.intent.category.BROWSABLE" />
           <data android:scheme="app.example.com"
                 android:path="/login"/>
       </intent-filter>
   </activity>
   ```
2. Create a `Client` instance (see
   [ExampleApp](https://github.schibsted.io/spt-identity/account-sdk-android-web/blob/master/app/src/main/java/com/schibsted/account/example/ExampleApp.kt)
   as reference):
   ```kotlin
   val clientConfig = ClientConfiguration(Environment.PRE, "<clientId>", "<redirect uri>")
   val okHttpClient = OkHttpClient.Builder().build() // this client instance should be shared within your app
   val client = Client(
       context = applicationContext,
       configuration = clientConfig,
       httpClient = okHttpClient
   )
   ```
   **Note:** If you need Retrofit support for making authenticated requests,
   wrap the above client instance in a `RetrofitClient` instance:

   ```kotlin
   val retrofitClient = RetrofitClient<YourRetrofitInterface>(
       client = client,
       serviceClass = YourRetrofitInterface::class.java,
       retrofitBuilder = Retrofit.Builder().baseUrl("https://your.api.com")
   )
   ```

   Then use this `RetrofitClient` instance as a replacement in all places where a `Client`
   object is expected below.

3. Initialise `AuthorizationManagementActivity` on app startup (see
   [ExampleApp](https://github.schibsted.io/spt-identity/account-sdk-android-web/blob/master/app/src/main/java/com/schibsted/account/example/ExampleApp.kt)
   as reference):
   ```kotlin
   class App : Application() {
       override fun onCreate() {
           super.onCreate()
   
           val completionIntent = Intent(this, <Activity started after completed login>)
           val cancelIntent = Intent(this, <Activity started after cancelled login>)
           AuthorizationManagementActivity.setup(
               client = client,
               completionIntent = PendingIntent.getActivity(this, 0, completionIntent, 0),
               cancelIntent = PendingIntent.getActivity(this, 0, cancelIntent, 0)
           )
       }
   }
     ``` 
   **Note:** `completionIntent` and `cancelIntent` are optional if you want to
   redirect user to a specific activity on login completion. You can also observe
   `AuthResultLiveData` and navigate on its result (see step 4. below).

4. Observe the `AuthResultLiveData` singleton instance to access the logged-in user (see
   [MainActivity](https://github.schibsted.io/spt-identity/account-sdk-android-web/blob/master/app/src/main/java/com/schibsted/account/example/MainActivity.kt)
   as reference):
   ```kotlin
   class MainActivity : AppCompatActivity() {
       override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
   
           AuthResultLiveData.get().observe(this,
               Observer { maybeAuthResult ->
                   val authResult = maybeAuthResult ?: return@Observer
                   authResult
                       .foreach { user ->
                           // TODO the user completed the login flow or an already logged-in user was resumed
                       }
                       .left().foreach(this::handleNotAuthedState)
               })
       }
   
       private fun handleNotAuthedState(state: NotAuthed): Unit {
           when {
               state === NotAuthed.NoLoggedInUser -> {
                   // TODO no logged-in user could be resumed or user was logged-out
               }
               state === NotAuthed.CancelledByUser -> {
                   // TODO the user cancelled the login flow (by pressing back or closing auth activity)
               }
               state === NotAuthed.AuthInProgress -> {
                   // TODO login flow is in progress
               }
               state is NotAuthed.LoginFailed -> {
                   // TODO some error occurred
               }
           }
       }
   }
   ```
5. If no user is is logged-in, start the login flow. For example on button click:
   ```kotlin
   loginButton.setOnClickListener { _ ->
       val authIntent = client.getAuthenticationIntent(this)
       startActivity(authIntent)
   }
   ```

#### Manual flow

The recommended usage (see above) automatically handles the following cases for you:

* If the user cancels the flow.
* Managing the back stack such that the Custom Tabs instance is cleared from it. See for example
  [this article](https://www.rallyhealth.com/back-stack-management-with-chrome-custom-tabs) for more
  details.
* Makes the logged-in user easily accessible via a `LiveData` instance.

If you want/need more control over the flow you can manage the flow manually,
by following these steps:

1. Create a new `Activity` that will receive the auth response via deep link and add it to your
   `AndroidManifest.xml`:
   ```xml
   <activity
       android:name="<your activity>">
       <intent-filter>
           <action android:name="android.intent.action.VIEW" />
           <category android:name="android.intent.category.DEFAULT" />
           <category android:name="android.intent.category.BROWSABLE" />
           <data android:scheme="https"
                 android:host="app.example.com"
                 android:path="/applogin"/>
       </intent-filter>
   </activity>
   ```
1. In that activity, make sure to call `Client.handleAuthenticationResponse` when receiving an
   intent via deep link:
   ```kotlin
   client.handleAuthenticationResponse(intent) { result ->
       result
           .foreach { user: User ->
               // TODO the user completed the login flow
           }
           .left().foreach { error: LoginError ->
               // TODO some error occurred
           }
   }
   ```
1. To get a logged-in user, first try to resume the last logged-in user:
   ```kotlin
   val user = client.resumeLastLoggedInUser()
   if (user != null) {
       // TODO user could successfully be resumed
   } else {
       // TODO no previously logged-in user could be resumed
   }
   ``` 
1. If no user could be resumed, start the login flow. For example on button click:
   ```kotlin
   loginButton.setOnClickListener { _ -> client.launchAuth(this) }
   ```

## Included functionality

* Single-sign on via web flows, offering one-click login for returning users (via shared cookies in
  CustomTabs).
    * With support for custom scope values, MFA, etc. See
      [here](https://docs.schibsted.io/schibsted-account/guides/authentication/) for more
      information.
* Automatic and transparent management of user tokens.
    * Authenticated requests to backend services can be done via
      [`User.makeAuthenticatedRequest`](https://pages.github.schibsted.io/spt-identity/account-sdk-android-web/webflows/com.schibsted.account.webflows.user/-user/make-authenticated-request.html)
      .

      If using Retrofit the authenticated request should be done via
      [`RetrofitClient.makeAuthenticatedRequest`](https://pages.github.schibsted.io/spt-identity/account-sdk-android-web/webflows/com.schibsted.account.webflows.user/-retrofit-client-facade/make-authenticated-request.html)
      .

      The SDK will automatically inject the user access token as a Bearer token in the HTTP
      Authorization request header. If the access token is rejected with a `401 Unauthorized`
      response (e.g. due to having expired), the SDK will try to use the refresh token to obtain a
      new access token and then retry the request once more.

      **Note:** If the refresh token request fails, due to the refresh token itself having expired
      or been invalidated by the user, the SDK will log the user out.
