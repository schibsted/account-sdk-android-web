/*
 * Copyright 2016 The AppAuth for Android Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * From: https://github.com/openid/AppAuth-Android
 * Notable changes:
 *  * Rewritten from Java to Kotlin.
 *  * Changed completionIntent and cancelIntent to be static properties to be initialised on each
 *    app (re)start to handle incoming auth responses even if AuthorizationManagementActivity has
 *    been destroyed. Result data is propagated via a LiveData instance instead of via extra intent
 *    data.
 *  * Updated variable names and code comments.
 *  * Removal of usage of custom types for compatibility.
 */


package com.schibsted.account.webflows.activities

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.tracking.SchibstedAccountTracker
import com.schibsted.account.webflows.tracking.SchibstedAccountTrackingEvent
import com.schibsted.account.webflows.util.Either.Left
import timber.log.Timber

/**
 * Stores state and handles events related to the authorization flow. It functions
 * to control the back stack, ensuring that the authorization activity will not be reachable
 * via the back button after the flow completes.
 *
 * The following diagram illustrates the operation of the activity:
 *
 * ```
 *                          Back Stack Towards Top
 *                +------------------------------------------>
 *
 * +------------+            +---------------+      +----------------+      +--------------+
 * |            |     (1)    |               | (2)  |                | (S1) |              |
 * | Initiating +----------->| Authorization +----->| Authorization  +----->| Redirect URI |
 * |  Activity  |            |  Management   |      |   Activity     |      |   Receiver   |
 * |            |            |   Activity    |<-----+ (e.g. browser) |      |   Activity   |
 * |            |            |               | (C1) |                |      |              |
 * +------------+            +--+--------+-+-+      +----------------+      +------+-------+
 *                           |  |        | ^                                       |
 *                           |  |        | |               (S2)                    |
 *                   +-------+  |        | +---------------------------------------+
 *                   |          |        +--------------------+
 *                   |          |               (C2/S3)       |
 *                   |          v (S4)                        v
 *             (C3)  |      +------------+           +--------+-----------+
 *                   |      |            |           |                    |
 *                   |      | Completion |           | AuthResultLiveData |
 *                   |      |  Activity  |           |                    |
 *                   |      |            |           +--------------------+
 *                   |      +------------+
 *                   |
 *                   |      +-------------+
 *                   |      |             |
 *                   +----->| Cancelation |
 *                          |  Activity   |
 *                          |             |
 *                          +-------------+
 * ```
 *
 * The process begins with an activity requesting that an authorization flow be started.
 *
 * - Step 1: Using an intent derived from {@link #createStartIntent}, this activity is
 *   started. The state delivered in this intent is recorded for future use.
 * - Step 2: The authorization intent, typically a browser tab, is started. At this point,
 *   depending on user action, we will either end up in a "completion" flow (S) or
 *   "cancelation flow" (C).
 *
 * - Cancelation (C) flow:
 *      - Step C1: If the user presses the back button or otherwise causes the
 *           authorization activity to finish, the AuthorizationManagementActivity will be
 *           recreated or restarted.
 *      - Step C2: `AuthResultLiveData` will be updated with {@link AuthError#CancelledByUser}.
 *      - Step C3: The cancellation activity is invoked via the specified PendingIntent.
 *
 * - Completion (S) flow:
 *      - Step S1: The authorization activity completes with a success of failure, and sends
 *        this result to {@link RedirectUriReceiverActivity}.
 *      - Step S2: {@link RedirectUriReceiverActivity} extracts the forwarded data, and
 *        invokes AuthorizationManagementActivity using an intent derived from
 *        {@link #createResponseHandlingIntent}. This intent has flag CLEAR_TOP set, which
 *        will result in both the authorization activity and
 *        {@link RedirectUriReceiverActivity} being destroyed, if necessary, such that
 *        AuthorizationManagementActivity is once again at the top of the back stack.
 *      - Step S3: `AuthResultLiveData` will be updated with {@link User} object.
 *        The AuthorizationManagementActivity finishes, removing itself from the back
 *        stack.
 *      - Step S4: The completion activity is invoked via the specified PendingIntent.
 */
class AuthorizationManagementActivity : Activity() {
    private var authStarted = false
    private lateinit var authIntent: Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: intent.extras
        if (bundle != null) {
            // We're either first starting this activity or resuming it after completed auth
            extractState(bundle)
        } else {
            /*
             * We possibly got here after the user cancelled/closed the flow, for example after
             * user has clicked verify email link to complete signup flow which also results in
             * final OAuth2 redirect.
             */
            authStarted = true
        }
    }

    override fun onResume() {
        super.onResume()

        AuthResultLiveData.get(client).update(Left(NotAuthed.AuthInProgress))

        /*
         * If this is the first run of the activity, start the auth intent.
         * Note that we do not finish the activity at this point, in order to remain on the back
         * stack underneath the authorization activity.
         */
        if (!authStarted) {
            startActivity(authIntent)
            authStarted = true
            return
        }

        /*
         * On a subsequent run, it must be determined whether we have returned to this activity
         * due to an OAuth2 redirect, or the user canceling the authorization flow. This can
         * be done by checking whether a response URI is available, which would be provided by
         * RedirectUriReceiverActivity. If it is not, we have returned here due to the user
         * pressing the back button, or the authorization activity finishing without
         * RedirectUriReceiverActivity having been invoked - this can occur when the user presses
         * the back button, or closes the browser tab.
         */
        if (intent.data != null) {
            handleAuthorizationComplete()
        } else {
            handleAuthorizationCanceled()
        }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_AUTHORIZATION_STARTED, authStarted)
        outState.putParcelable(KEY_AUTH_INTENT, authIntent)
        super.onSaveInstanceState(outState)
    }

    private fun handleAuthorizationComplete() {
        AuthResultLiveData.get(client).update(intent)
        completionIntent?.send()
    }

    private fun handleAuthorizationCanceled() {
        Timber.d("Authorization flow canceled by user")
        SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.UserLoginCanceled)
        AuthResultLiveData.get(client).update(Left(NotAuthed.CancelledByUser))
        cancelIntent?.send()
    }

    private fun extractState(state: Bundle?) {
        checkNotNull(state) { "No state to extract" }
        authIntent = state.getParcelable(KEY_AUTH_INTENT)!!
        authStarted = state.getBoolean(KEY_AUTHORIZATION_STARTED, false)
    }

    companion object {
        private const val KEY_AUTH_INTENT = "authIntent"
        private const val KEY_AUTHORIZATION_STARTED = "authStarted"

        internal var completionIntent: PendingIntent? = null
        internal var cancelIntent: PendingIntent? = null
        internal lateinit var client: Client

        @JvmStatic
        fun setup(
            client: Client,
            completionIntent: PendingIntent? = null,
            cancelIntent: PendingIntent? = null
        ) {
            Companion.client = client
            AuthResultLiveData.create(client)
            Companion.completionIntent = completionIntent
            Companion.cancelIntent = cancelIntent
        }

        /**
         * Creates an intent to start an authorization flow.
         * @param context the package context for the app.
         * @param authIntent the intent to be used to get authorization from the user.
         * @throws IllegalStateException if {@link AuthorizationManagementActivity#setup) has not
         *  been called before this
         */
        internal fun createStartIntent(context: Context, authIntent: Intent): Intent {
            if (AuthResultLiveData.getIfInitialised() == null) {
                throw IllegalStateException("AuthorizationManagementActivity.setup must be called before this")
            }
            return createBaseIntent(context).apply {
                putExtra(KEY_AUTH_INTENT, authIntent)
            }
        }

        /**
         * Creates an intent to handle the completion of an authorization flow. This restores
         * the original [AuthorizationManagementActivity] that was created at the start of the flow.
         * @param context the package context for the app.
         * @param responseUri the response URI, which carries the parameters describing the response.
         */
        internal fun createResponseHandlingIntent(context: Context, responseUri: Uri?): Intent {
            return createBaseIntent(context).apply {
                data = responseUri
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }

        private fun createBaseIntent(context: Context): Intent {
            return Intent(context, AuthorizationManagementActivity::class.java)
        }
    }
}
