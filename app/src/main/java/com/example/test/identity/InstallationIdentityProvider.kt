package com.example.test.identity

/**
 * Anonymous install-scoped identity. It contains no account, email, phone
 * number, or ANDROID_ID.
 */
data class InstallationIdentity(
    val installationId: String,
    val publicKeyBase64Url: String,
)

interface InstallationIdentityProvider {
    suspend fun getOrCreate(): InstallationIdentity

    /** Returns a URL-safe base64 ECDSA signature for the exact payload. */
    suspend fun sign(payload: ByteArray): String

    /** Deletes the install key. The next getOrCreate call creates a new identity. */
    suspend fun clear()
}
