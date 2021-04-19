package com.schibsted.account.webflows.api

import com.google.gson.annotations.SerializedName
import java.util.*

data class UserProfileResponse(
    val uuid: String? = null,
    val userId: String? = null,
    val status: Int? = null,
    val email: String? = null,
    val emailVerified: String? = null,
    val emails: List<Email>? = null,
    val phoneNumber: String? = null,
    val phoneNumberVerified: String? = null,
    val phoneNumbers: List<PhoneNumber>? = null,
    val displayName: String? = null,
    val name: Name? = null,
    val addresses: Map<Address.AddressType, Address>? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val accounts: Map<String, Account>? = null,
    val merchants: List<Int>? = null,
    val published: String? = null,
    val verified: String? = null,
    val updated: String? = null,
    val passwordChanged: String? = null,
    val lastAuthenticated: String? = null,
    val lastLoggedIn: String? = null,
    val locale: String? = null,
    val utcOffset: String? = null,
)

data class Email(
    val value: String? = null,
    val type: String? = null,
    val primary: Boolean? = null,
    val verified: Boolean? = null,
    val verifiedTime: String? = null
)

data class PhoneNumber(
    val value: String? = null,
    val type: String? = null,
    val primary: Boolean? = null,
    val verified: Boolean? = null,
    val verifiedTime: String? = null
)

data class Name(
    val givenName: String? = null,
    val familyName: String? = null,
    val formatted: String? = null
)

data class Account(
    val id: String? = null,
    val accountName: String? = null,
    val domain: String? = null,
    val connected: String? = null
)

data class Address(
    val formatted: String? = null,
    val streetAddress: String? = null,
    val postalCode: String? = null,
    val locality: String? = null,
    val region: String? = null,
    val country: String? = null,
    val type: AddressType? = null
) {

    enum class AddressType {
        @SerializedName("home")
        HOME,
        @SerializedName("delivery")
        DELIVERY,
        @SerializedName("invoice")
        INVOICE;

        override fun toString(): String = super.toString().toLowerCase(Locale.ROOT)
    }
}
