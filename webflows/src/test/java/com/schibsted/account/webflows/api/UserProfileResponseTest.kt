package com.schibsted.account.webflows.api

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class UserProfileResponseTest {
    @Test
    fun userProfileResponseFiltersEmptyBirthday() {
        val jsonString = """{"birthday": "0000-00-00"}"""
        val parsed = Gson().fromJson(jsonString, UserProfileResponse::class.java)
        assertNull(parsed.birthday)
    }

    @Test
    fun userProfileResponseHandlesPossibleBoolDates() {
        val jsonString = """
        {
            "emailVerified": false,
            "phoneNumberVerified": false
        }
        """
        val parsed = Gson().fromJson(jsonString, UserProfileResponse::class.java)
        assertNull(parsed.emailVerified)
        assertNull(parsed.phoneNumberVerified)
    }

    @Test
    fun userProfileResponseHandlesEmptyAddressArray() {
        val jsonString = """{"addresses": []}"""
        val parsed = Gson().fromJson(jsonString, UserProfileResponse::class.java)
        assertEquals(true, parsed.addresses?.isEmpty())
    }

    @Test
    fun fullUserProfileResponse() {
        val jsonString = javaClass.getResource("/user-profile-response.json")!!.readText()
        val parsed = Gson().fromJson(jsonString, UserProfileResponse::class.java)

        val expectedProfileResponse = UserProfileResponse(
            uuid = "96085e85-349b-4dbf-9809-fa721e7bae46",
            userId = "12345",
            status = 1,
            email = "test@example.com",
            emailVerified = "1970-01-01 00:00:00",
            emails = listOf(
                Email(
                    "test@example.com",
                    "other",
                    true,
                    true,
                    "1970-01-01 00:00:00"
                )
            ),
            phoneNumber = "+46123456",
            phoneNumberVerified = null,
            phoneNumbers = listOf(PhoneNumber("+46123456", "other", false, false)),
            displayName = "Unit test",
            name = Name("Unit", "Test", "Unit Test"),
            addresses = mapOf(
                Address.AddressType.HOME to Address(
                    "12345 Test, Sverige",
                    "Test",
                    "12345",
                    "Test locality",
                    "Test region",
                    "Sverige",
                    Address.AddressType.HOME
                )
            ),
            gender = "withheld",
            birthday = "1970-01-01 00:00:00",
            accounts = mapOf(
                "client1" to Account(
                    "client1",
                    "Example",
                    "example.com",
                    "1970-01-01 00:00:00"
                )
            ),
            merchants = listOf(12345, 54321),
            published = "1970-01-01 00:00:00",
            verified = "1970-01-01 00:00:00",
            updated = "1971-01-01 00:00:00",
            passwordChanged = "1970-01-01 00:00:00",
            lastAuthenticated = "1970-01-01 00:00:00",
            lastLoggedIn = "1970-01-01 00:00:00",
            mfaEnabled = true,
            mfaMethods = listOf(MfaMethod(MfaMethod.MfaType.TOTP), MfaMethod(MfaMethod.MfaType.BANKID), MfaMethod(MfaMethod.MfaType.SMS)),
            locale = "sv_SE",
            utcOffset = "+02:00"
        )
        assertEquals(expectedProfileResponse, parsed)
    }
}
