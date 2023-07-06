package com.schibsted.account.webflows.tracking

sealed class SchibstedAccountTrackingEvent {
    object LoginPromptCreated : SchibstedAccountTrackingEvent()
    object LoginPromptView : SchibstedAccountTrackingEvent()
    object LoginPromptLeave : SchibstedAccountTrackingEvent()
    object LoginPromptDestroyed : SchibstedAccountTrackingEvent()
    object LoginPromptClickToLogin : SchibstedAccountTrackingEvent()
    object LoginPromptClickToContinueWithoutLogin : SchibstedAccountTrackingEvent()
    object LoginPromptClickOutside : SchibstedAccountTrackingEvent()
}