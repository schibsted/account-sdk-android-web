package com.schibsted.account.webflows.tracking

interface SchibstedAccountTrackerStore {
    fun addTrackingListener(trackingListener: SchibstedAccountTrackingListener)
    fun removeTrackingListener(trackingListener: SchibstedAccountTrackingListener)

    companion object : SchibstedAccountTrackerStore {
        private val accountListenersList: MutableList<SchibstedAccountTrackingListener> =
            mutableListOf()

        /**
         * Adds listener for tracking events
         */
        override fun addTrackingListener(trackingListener: SchibstedAccountTrackingListener) {
            accountListenersList.add(trackingListener)
        }

        /**
         * Remove listener for tracking events
         * This api might be useful for unit testing as mock listeners should be
         * removed after all tests.
         */
        override fun removeTrackingListener(trackingListener: SchibstedAccountTrackingListener) {
            accountListenersList.remove(trackingListener)
        }

        internal fun notifyListeners(event: SchibstedAccountTrackingEvent) {
            accountListenersList.forEach {
                it.onEvent(event)
            }
        }
    }
}

interface SchibstedAccountTrackingListener {
    fun onEvent(event: SchibstedAccountTrackingEvent)
}