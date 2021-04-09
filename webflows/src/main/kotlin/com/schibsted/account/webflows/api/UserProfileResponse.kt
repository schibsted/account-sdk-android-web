package com.schibsted.account.webflows.api

data class UserProfileResponse(
    val userId: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val displayName: String? = null,
    val name: Name? = null
)

data class Name(
    val givenName: String? = null,
    val familyName: String? = null,
    val formatted: String? = null
)
