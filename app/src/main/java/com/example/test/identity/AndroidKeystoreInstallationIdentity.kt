package com.example.test.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A random P-256 key generated inside Android Keystore. The public-key digest
 * is the installation id; uninstalling/clearing app identity creates a new one.
 */
class AndroidKeystoreInstallationIdentity(
    context: Context,
) : InstallationIdentityProvider {
    private val keyAlias = "${context.applicationContext.packageName}.installation_identity.v1"

    override suspend fun getOrCreate(): InstallationIdentity = withContext(Dispatchers.IO) {
        KEY_MUTEX.withLock {
            val entry = loadOrCreateEntry()
            val publicKey = entry.certificate.publicKey.encoded
            InstallationIdentity(
                installationId = "inst_${sha256Base64Url(publicKey)}",
                publicKeyBase64Url = publicKey.toBase64Url(),
            )
        }
    }

    override suspend fun sign(payload: ByteArray): String = withContext(Dispatchers.IO) {
        KEY_MUTEX.withLock {
            val entry = loadOrCreateEntry()
            Signature.getInstance("SHA256withECDSA").run {
                initSign(entry.privateKey)
                update(payload)
                sign().toBase64Url()
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        KEY_MUTEX.withLock {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
                if (containsAlias(keyAlias)) deleteEntry(keyAlias)
            }
            Unit
        }
    }

    private fun loadOrCreateEntry(): KeyStore.PrivateKeyEntry {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry)?.let { return it }

        KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE,
        ).apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
        }.generateKeyPair()

        val refreshedStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return requireNotNull(
            refreshedStore.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry,
        ) { "Android Keystore installation key was not created" }
    }

    private fun sha256Base64Url(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).toBase64Url()

    private fun ByteArray.toBase64Url(): String = Base64.encodeToString(
        this,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        val KEY_MUTEX = Mutex()
    }
}
