package com.schibsted.account.webflows.tracking

import com.schibsted.account.webflows.BuildConfig

const val PACKAGE_NAME = "account-sdk-android-web"
const val PROVIDER_COMPONENT = "schibsted-account"

sealed class SchibstedAccountTrackingEvent {
    /**
     * This value needs to be mapped to the "provider.component" property of the Pulse event
     */
    val providerComponent = PROVIDER_COMPONENT

    /**
     * This value needs to be mapped to the "deploy_tag" property of the Pulse event
     */
    val deployTag = "${PACKAGE_NAME}-${BuildConfig.VERSION_NAME}"

    object LoginPromptCreated : SchibstedAccountTrackingEvent()

    object LoginPromptView : SchibstedAccountTrackingEvent()

    object LoginPromptLeave : SchibstedAccountTrackingEvent()

    object LoginPromptDestroyed : SchibstedAccountTrackingEvent()

    object LoginPromptClickToLogin : SchibstedAccountTrackingEvent()

    object LoginPromptClickToContinueWithoutLogin : SchibstedAccountTrackingEvent()

    object LoginPromptClickOutside : SchibstedAccountTrackingEvent()

    object LoginPromptContentProviderInsert : SchibstedAccountTrackingEvent()

    object LoginPromptContentProviderDelete : SchibstedAccountTrackingEvent()

    object UserLoginSuccessful : SchibstedAccountTrackingEvent()

    object UserLoginFailed : SchibstedAccountTrackingEvent()

    object UserLoginCanceled : SchibstedAccountTrackingEvent()
}
