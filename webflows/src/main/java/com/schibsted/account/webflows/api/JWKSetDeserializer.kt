package com.schibsted.account.webflows.api

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.nimbusds.jose.jwk.JWKSet
import java.lang.reflect.Type
import java.text.ParseException

internal class JWKSetDeserializer : JsonDeserializer<JWKSet> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): JWKSet {
        try {
            return JWKSet.parse(json.toString())
        } catch (e: ParseException) {
            throw JsonParseException(e)
        }
    }
}
