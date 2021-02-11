package com.schibsted.account.android.webflows.client

import java.net.URL

class ClientConfiguration(_serverUrl: String, val clientId: String, val clientSecret: String, val redirectUrl: URL) {
    val serverUrl = URL(_serverUrl)

}

