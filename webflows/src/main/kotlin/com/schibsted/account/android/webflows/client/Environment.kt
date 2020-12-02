package com.schibsted.account.android.webflows.client

enum class Environment(val url: String) {
    PRO_COM("https://login.schibsted.com"),
    PRO_NO("https://payment.schibsted.no"),
    PRO_FI("https://login.schibsted.fi"),
    PRE("https://identity-pre.schibsted.com")
}