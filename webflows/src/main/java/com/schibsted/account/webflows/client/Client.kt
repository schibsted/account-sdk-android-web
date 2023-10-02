package com.schibsted.account.webflows.client

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.FragmentManager
import com.schibsted.account.webflows.activities.AuthorizationManagementActivity
import com.schibsted.account.webflows.api.HttpError
import com.schibsted.account.webflows.api.SchibstedAccountApi
import com.schibsted.account.webflows.loginPrompt.LoginPromptConfig
import com.schibsted.account.webflows.loginPrompt.LoginPromptManager
import com.schibsted.account.webflows.loginPrompt.SessionInfoManager
import com.schibsted.account.webflows.persistence.EncryptedSharedPrefsStorage
import com.schibsted.account.webflows.persistence.MigratingSessionStorage
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.persistence.SharedPrefsStorage
import com.schibsted.account.webflows.persistence.StateStorage
import com.schibsted.account.webflows.persistence.StorageError
import com.schibsted.account.webflows.token.TokenError
import com.schibsted.account.webflows.token.TokenHandler
import com.schibsted.account.webflows.token.UserTokens
import com.schibsted.account.webflows.tracking.SchibstedAccountTracker
import com.schibsted.account.webflows.tracking.SchibstedAccountTrackingEvent
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import com.schibsted.account.webflows.util.Util
import com.schibsted.account.webflows.util.Util.isCustomTabsSupported
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.Date
import kotlin.coroutines.resume

/**  Represents a client registered with Schibsted account. */
class Client {
    internal val httpClient: OkHttpClient
    internal val schibstedAccountApi: SchibstedAccountApi
    internal val configuration: ClientConfiguration

    private val tokenHandler: TokenHandler
    private val stateStorage: StateStorage
    private val sessionStorage: SessionStorage
    private val urlBuilder: UrlBuilder
    private var logoutCallback: (() -> Unit)? = null

    @JvmOverloads
    constructor (
        context: Context,
        configuration: ClientConfiguration,
        httpClient: OkHttpClient,
        logoutCallback: (() -> Unit)? = null
    ) {
        this.configuration = configuration
        stateStorage = StateStorage(context.applicationContext)

        val encryptedStorage = EncryptedSharedPrefsStorage(context.applicationContext)
        val sharedPrefsStorage =
            SharedPrefsStorage(context.applicationContext, configuration.serverUrl.toString())

        sessionStorage = MigratingSessionStorage(
            newStorage = sharedPrefsStorage,
            previousStorage = encryptedStorage
        )

        schibstedAccountApi =
            SchibstedAccountApi(configuration.serverUrl.toString().toHttpUrl(), httpClient)
        tokenHandler = TokenHandler(configuration, schibstedAccountApi)
        this.httpClient = httpClient
        this.urlBuilder = UrlBuilder(configuration, stateStorage, AUTH_STATE_KEY)
        this.logoutCallback = logoutCallback
    }

    internal constructor (
        configuration: ClientConfiguration,
        stateStorage: StateStorage,
        sessionStorage: SessionStorage,
        httpClient: OkHttpClient,
        tokenHandler: TokenHandler,
        schibstedAccountApi: SchibstedAccountApi
    ) {
        this.configuration = configuration
        this.stateStorage = stateStorage
        this.sessionStorage = sessionStorage
        this.tokenHandler = tokenHandler
        this.httpClient = httpClient
        this.schibstedAccountApi = schibstedAccountApi
        this.urlBuilder = UrlBuilder(configuration, stateStorage, AUTH_STATE_KEY)
    }

    /**
     * Start login flow.
     *
     * Requires [AuthorizationManagementActivity.setup] to have been called before this.
     *
     * @param authRequest Authentication request parameters.
     */
    @JvmOverloads
    fun getAuthenticationIntent(
        context: Context,
        authRequest: AuthRequest = AuthRequest()
    ): Intent {
        val loginUrl = generateLoginUrl(authRequest)
        val intent: Intent = if (isCustomTabsSupported(context)) {
            buildCustomTabsIntent()
                .apply {
                    intent.data = loginUrl
                }.intent
        } else {
            Intent(Intent.ACTION_VIEW, loginUrl).addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return AuthorizationManagementActivity.createStartIntent(context, intent)
    }

    /**
     * Start auth activity manually.
     *
     * @param authRequest Authentication request parameters.
     */
    @JvmOverloads
    fun launchAuth(context: Context, authRequest: AuthRequest = AuthRequest()) {
        val loginUrl = generateLoginUrl(authRequest)
        if (isCustomTabsSupported(context)) {
            buildCustomTabsIntent().launchUrl(context, loginUrl)
        } else {
            val intent = Intent(Intent.ACTION_VIEW, loginUrl).addCategory(Intent.CATEGORY_BROWSABLE)
            context.startActivity(intent)
        }
    }

    private fun buildCustomTabsIntent(): CustomTabsIntent {
        return CustomTabsIntent.Builder()
            .build()
    }

    private fun generateLoginUrl(authRequest: AuthRequest): Uri {
        val loginUrl = urlBuilder.loginUrl(authRequest)
        Timber.d("Login url: $loginUrl")
        return Uri.parse(loginUrl)
    }

    /**
     * Call this with the intent received via deep link to complete the login flow.
     *
     * This only needs to be used if manually starting the login flow using [launchAuth].
     * If using [getAuthenticationIntent] this step will be handled for you.
     */
    fun handleAuthenticationResponse(intent: Intent, callback: LoginResultHandler) {
        val authResponse = intent.data?.query ?: return callback(
            Left(LoginError.UnexpectedError("No authentication response"))
        )
        handleAuthenticationResponse(authResponse, callback)
    }

    private fun handleAuthenticationResponse(
        authResponseParameters: String,
        callback: LoginResultHandler
    ) {
        val authResponse = Util.parseQueryParameters(authResponseParameters)
        val stored = stateStorage.getValue(AUTH_STATE_KEY, AuthState::class)
            ?: return callback(Left(LoginError.AuthStateReadError))

        val eidUserCancelError = mapOf(
            "error" to "access_denied",
            "error_description" to "EID authentication was canceled by the user"
        )
        val error = authResponse["error"]
        val errorDescription = authResponse["error_description"]
        if (error != null && error == eidUserCancelError["error"] && errorDescription == eidUserCancelError["error_description"]) {
            val oauthError = OAuthError(error, errorDescription)
            callback(Left(LoginError.CancelledByUser(oauthError)))
            return
        }

        if (stored.state != authResponse["state"]) {
            callback(Left(LoginError.UnsolicitedResponse))
            return
        }

        stateStorage.removeValue(AUTH_STATE_KEY)

        if (error != null) {
            val oauthError = OAuthError(error, errorDescription)
            callback(Left(LoginError.AuthenticationErrorResponse(oauthError)))
            return
        }

        val authCode = authResponse["code"]
            ?: return callback(Left(LoginError.UnexpectedError("Missing authorization code in authentication response")))
        makeTokenRequest(authCode, stored) { storedUserSession ->
            storedUserSession
                .onSuccess { session ->
                    sessionStorage.save(session)
                    SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.UserLoginSuccessful)
                    callback(Right(User(this, session.userTokens)))
                }
                .onFailure { err ->
                    Timber.d("Token error response: $err")
                    SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.UserLoginFailed)
                    val oauthError = err.toOauthError()
                    if (oauthError != null) {
                        callback(Left(LoginError.TokenErrorResponse(oauthError)))
                    } else {
                        callback(Left(LoginError.UnexpectedError(err.toString())))
                    }
                }
        }
    }

    internal fun makeTokenRequest(
        authCode: String,
        authState: AuthState?,
        callback: (Either<TokenError, StoredUserSession>) -> Unit
    ) {
        tokenHandler.makeTokenRequest(authCode, authState) { result ->
            val session: Either<TokenError, StoredUserSession> = result.map { tokenResponse ->
                StoredUserSession(
                    configuration.clientId,
                    tokenResponse.userTokens,
                    Date()
                )
            }
            callback(session)
        }
    }

    /** Resume any previously logged-in user session */
    fun resumeLastLoggedInUser(callback: (Either<StorageError, User?>) -> Unit) {
        sessionStorage.get(configuration.clientId) { result ->
            result
                .onSuccess { storedUserSession: StoredUserSession? ->
                    if (storedUserSession == null) {
                        callback(Right(null))
                    } else {
                        val user = User(this, storedUserSession.userTokens)
                        callback(Right(user))
                    }
                }
                .onFailure {
                    callback(Left(it))
                }
        }
    }

    internal fun destroySession() {
        sessionStorage.remove(configuration.clientId)

        logoutCallback?.invoke()
    }

    internal fun refreshTokensForUser(user: User): Either<RefreshTokenError, UserTokens> {
        val refreshToken =
            user.tokens?.refreshToken ?: return Left(RefreshTokenError.NoRefreshToken)

        return when (val result = tokenHandler.makeTokenRequest(refreshToken, scope = null)) {
            is Right -> {
                val tokens = user.tokens
                if (tokens != null) {
                    val newAccessToken = result.value.access_token
                    val newRefreshToken = result.value.refresh_token ?: refreshToken
                    val userTokens = tokens.copy(
                        accessToken = newAccessToken,
                        refreshToken = newRefreshToken
                    )
                    user.tokens = userTokens
                    val userSession =
                        StoredUserSession(configuration.clientId, userTokens, Date())
                    sessionStorage.save(userSession)
                    Timber.d("User tokens refreshed")
                    Right(userTokens)
                } else {
                    Timber.i("User has logged-out during token refresh, discarding new tokens.")
                    Left(RefreshTokenError.UnexpectedError("User has logged-out during token refresh"))
                }
            }

            is Left -> {
                Timber.e("Failed to refresh token: $result")
                Left(RefreshTokenError.RefreshRequestFailed(result.value.cause))
            }
        }
    }

    /**
     * Show native login prompt if user already has a valid session on device and if no user session is found in the app.
     *
     * @param supportFragmentManager Activity's Fragment manager.
     * @param isCancelable set if loginPrompt should be cancelable by user.
     */
    @JvmOverloads
    suspend fun requestLoginPrompt(
        context: Context,
        supportFragmentManager: FragmentManager,
        isCancelable: Boolean = true
    ) {
        val internalSessionFound = hasSessionStorage(configuration.clientId)

        if (!internalSessionFound && userHasSessionOnDevice(context.applicationContext)) {
            LoginPromptManager(
                LoginPromptConfig(
                    this.getAuthenticationIntent(context),
                    isCancelable
                )
            ).showLoginPromptIfAbsent(supportFragmentManager)
        }
    }

    private suspend fun hasSessionStorage(clientId: String) =
        suspendCancellableCoroutine<Boolean> { continuation ->
            sessionStorage.get(clientId) { result ->
                result
                    .onSuccess { continuation.resume(true) }
                    .onFailure { continuation.resume(false) }
            }
        }

    private suspend fun userHasSessionOnDevice(context: Context): Boolean {
        return SessionInfoManager(
            context,
            configuration.serverUrl.toString()
        ).isUserLoggedInOnTheDevice()
    }

    internal companion object {
        const val AUTH_STATE_KEY = "AuthState"
    }
}

data class OAuthError(val error: String, val errorDescription: String?) {
    companion object {
        fun fromJson(json: String): OAuthError? {
            return try {
                val parsed = JSONObject(json)
                OAuthError(
                    parsed.getString("error"),
                    parsed.optString("error_description")
                )
            } catch (e: JSONException) {
                null
            }
        }
    }
}

private fun TokenError.toOauthError(): OAuthError? {
    if (this is TokenError.TokenRequestError && cause is HttpError.ErrorResponse && cause.body != null) {
        return OAuthError.fromJson(cause.body)
    }

    return null
}

sealed class LoginError {
    /** Failed to read stored [AuthState]. */
    object AuthStateReadError : LoginError()

    /** Auth response not matching stored [AuthState]. */
    object UnsolicitedResponse : LoginError()

    /**
     * Authentication error response.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthError" target="_top">Authentication Error Response</a>
     */

    data class AuthenticationErrorResponse(val error: OAuthError) : LoginError()

    /**
     * Token error response.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#TokenErrorResponse" target="_top">Token Error Response</a>
     */
    data class TokenErrorResponse(val error: OAuthError) : LoginError()

    /** User canceled login. */
    data class CancelledByUser(val error: OAuthError) : LoginError()

    /** Something went wrong. */
    data class UnexpectedError(val message: String) : LoginError()
}

sealed class RefreshTokenError {
    object NoRefreshToken : RefreshTokenError()
    object ConcurrentRefreshFailure : RefreshTokenError()
    object UserWasLoggedOut : RefreshTokenError()
    data class RefreshRequestFailed(val error: HttpError) : RefreshTokenError()
    data class UnexpectedError(val message: String) : RefreshTokenError()
}

typealias LoginResultHandler = (Either<LoginError, User>) -> Unit
