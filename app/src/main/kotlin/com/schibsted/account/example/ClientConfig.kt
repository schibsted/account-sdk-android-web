package com.schibsted.account.example

import com.schibsted.account.android.webflows.client.ClientConfiguration
import com.schibsted.account.android.webflows.client.Environment

object ClientConfig {
    val instance: ClientConfiguration = ClientConfiguration(
        Environment.PRE,
        "602525f2b41fa31789a95aa8",
        "com.sdk-example.pre.602525f2b41fa31789a95aa8://login"
    )
}
