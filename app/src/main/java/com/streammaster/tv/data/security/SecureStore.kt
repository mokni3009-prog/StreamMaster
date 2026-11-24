package com.streammaster.tv.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SEC-02 / SEC-03 / SEC-04 (2026-11-24) — the parental PIN, the Google
 * Drive OAuth tokens and the OpenSubtitles API key used to be stored as
 * plaintext in DataStore. They are now encrypted at rest with a key that
 * lives in the Android KeyStore and never leaves it.
 *
 * No new Gradle dependency: plain Android KeyStore + AES/GCM, using only
 * platform APIs (minSdk 23 — [KeyGenParameterSpec] is available since
 * API 23). Deliberately NOT [com.streammaster.tv.data.backup.BackupCrypto]:
 * that helper needs a user-supplied password (storing one would recreate
 * the same problem in a circle) and deriving a key from a 4-digit PIN
 * would be trivially brute-forceable.
 *
 * Format: `"v1:" + Base64.NO_WRAP( IV(12 bytes) || ciphertext || GCM tag )`.
 * A fresh random IV is generated for every encryption (GCM requires a
 * unique IV per key), so the same plaintext encrypts differently each
 * time. The GCM tag (128 bit) authenticates the ciphertext — any
 * tampering fails decryption instead of returning garbage.
 *
 * Failure policy (never crash the app over a secret):
 * - [encrypt] returns null when the key cannot be loaded/generated or
 *   the cipher fails; callers keep the previously stored value instead
 *   of writing plaintext.
 * - [decrypt] returns `""` for blank input and for any undecryptable
 *   value, and returns legacy plaintext as-is (no `v1:` prefix) so
 *   values stored before this change keep working until the one-shot
 *   migration rewrites them encrypted.
 * - A blank value is stored as `""` (nothing secret to protect, and it
 *   keeps "no PIN / no token / no key" meaning exactly "absent").
 *
 * Key loss: KeyStore keys are NOT part of Android Auto Backup, so if a
 * device is restored from backup the DataStore file may come back
 * without its key. In that case [decrypt] returns `""` — the PIN is
 * treated as unset and Drive / OpenSubtitles as unlinked / key-less, and
 * the coach simply re-enters them. Secrets are never lost silently into
 * a wrong value.
 */
@Singleton
class SecureStore @Inject constructor() {

    companion object {
        private const val TAG = "StreamMaster"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "streammaster-secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_BYTES = 12

        /** Prefix marking an encrypted value. Anything stored without it
         *  is legacy plaintext awaiting the one-shot migration. */
        const val PREFIX = "v1:"
    }

    /** True when [value] is already encrypted by [encrypt]. */
    fun isEncrypted(value: String?): Boolean =
        value != null && value.startsWith(PREFIX)

    /**
     * Encrypts [plain] to the `v1:` format, or null when encryption is
     * impossible (KeyStore unavailable). Blank input returns `""`.
     */
    fun encrypt(plain: String): String? {
        if (plain.isEmpty()) return ""
        return try {
            val key = getOrCreateKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "SecureStore: encryption failed", e)
            null
        }
    }

    /**
     * Decrypts a value produced by [encrypt]. Legacy plaintext (no `v1:`
     * prefix) is returned unchanged so pre-existing values keep working.
     * Returns `""` for blank input and for any value that cannot be
     * decrypted (corrupt data, lost KeyStore key) — never throws.
     */
    fun decrypt(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        if (!stored.startsWith(PREFIX)) return stored // legacy plaintext
        return try {
            val combined = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            if (combined.size <= IV_BYTES) return ""
            val iv = combined.copyOfRange(0, IV_BYTES)
            val ciphertext = combined.copyOfRange(IV_BYTES, combined.size)
            val key = getOrCreateKey() ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "SecureStore: decryption failed", e)
            ""
        }
    }

    /**
     * Encrypts [plain] for storage, keeping [previousStored] when
     * encryption fails — so a KeyStore failure never overwrites a good
     * value with plaintext and never loses the old value. Blank input
     * returns `""` (clears the secret).
     */
    fun encryptForStore(plain: String, previousStored: String?): String {
        if (plain.isEmpty()) return ""
        return encrypt(plain) ?: (previousStored ?: "")
    }

    /**
     * Loads the AES key from the Android KeyStore, generating it on first
     * use. No user authentication is required for the key, so it is not
     * invalidated by lock-screen / biometric changes and it works on
     * first unlock after boot without prompting the user.
     */
    private fun getOrCreateKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.w(TAG, "SecureStore: key load/generate failed", e)
            null
        }
    }
}
