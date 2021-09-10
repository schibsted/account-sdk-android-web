package com.schibsted.account.webflows.api

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.util.*

data class UserProfileResponse(
    val uuid: String? = null,
    val userId: String? = null,
    val status: Int? = null,
    val email: String? = null,
    @JsonAdapter(StringOrIgnoreTypeAdapter::class)
    val emailVerified: String? = null,
    val emails: List<Email>? = null,
    val phoneNumber: String? = null,
    @JsonAdapter(StringOrIgnoreTypeAdapter::class)
    val phoneNumberVerified: String? = null,
    val phoneNumbers: List<PhoneNumber>? = null,
    val displayName: String? = null,
    val name: Name? = null,
    val addresses: Map<Address.AddressType, Address>? = null,
    val gender: String? = null,
    @JsonAdapter(BirthdayTypeAdapter::class)
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
    val utcOffset: String? = null
)

interface Identifier {
    val value: String?
    val type: String?
    val primary: Boolean?
    val verified: Boolean?
    val verifiedTime: String?
}

data class Email(
    override val value: String? = null,
    override val type: String? = null,
    override val primary: Boolean? = null,
    override val verified: Boolean? = null,
    override val verifiedTime: String? = null
) : Identifier

data class PhoneNumber(
    override val value: String? = null,
    override val type: String? = null,
    override val primary: Boolean? = null,
    override val verified: Boolean? = null,
    override val verifiedTime: String? = null
) : Identifier

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

private class BirthdayTypeAdapter : TypeAdapter<String>() {
    override fun write(out: JsonWriter, value: String?) {
        if (value != null) {
            out.value(value)
        }
    }

    override fun read(`in`: JsonReader): String? {
        val value = `in`.nextString()

        return if (value == "0000-00-00") {
            null
        } else {
            value
        }
    }
}

private class StringOrIgnoreTypeAdapter : TypeAdapter<String>() {
    override fun write(out: JsonWriter, value: String?) {
        if (value != null) {
            out.value(value)
        }
    }

    override fun read(`in`: JsonReader): String? {
        return try {
            `in`.nextString()
        } catch (e: IllegalStateException) {
            `in`.skipValue()
            null
        }
    }
}
