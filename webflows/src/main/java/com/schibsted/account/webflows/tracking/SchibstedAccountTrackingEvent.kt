package com.schibsted.account.webflows.tracking

sealed class SchibstedAccountTrackingEvent {
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