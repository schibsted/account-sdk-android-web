package com.schibsted.account.webflows.client

data class SchibstedAccountConsents(
    val advertising: Status,
    val analytics: Status,
    val marketing: Status,
    val personalization: Status,
    val source: String = "cmp",
) {
    enum class Status {
        ACCEPTED, REJECTED, UNKNOWN,
    }
}
