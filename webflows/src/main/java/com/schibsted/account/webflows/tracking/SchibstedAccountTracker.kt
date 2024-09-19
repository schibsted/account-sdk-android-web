package com.schibsted.account.webflows.tracking

internal object SchibstedAccountTracker {
    internal fun track(event: SchibstedAccountTrackingEvent) {
        SchibstedAccountTrackerStore.notifyListeners(event)
    }
}
