package com.schibsted.account.webflows.client

import java.net.URL

enum class Environment(val url: URL) {
    PRO_COM(URL("https://login.schibsted.com")),
    PRO_NO(URL("https://payment.schibsted.no")),
    PRO_FI(URL("https://login.schibsted.fi")),
    PRE(URL("https://identity-pre.schibsted.com"))
}