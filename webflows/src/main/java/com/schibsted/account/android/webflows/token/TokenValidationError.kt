package com.schibsted.account.android.webflows.token

abstract class TokenValidationError(msg: String) : Exception(msg)

class SignatureValidationError(msg: String): TokenValidationError(msg)
class FailedToDecodePayloadError(msg: String): TokenValidationError(msg)
class MissingIdTokenError(msg: String): TokenValidationError(msg)
class InvalidNonce(msg: String): TokenValidationError(msg)
class MissingExpectedAMRValue(msg: String): TokenValidationError(msg)
class InvalidIssuerError(msg: String): TokenValidationError(msg)
class InvalidAudienceError(msg: String): TokenValidationError(msg)
class ExpiredTokenError(msg: String): TokenValidationError(msg)


