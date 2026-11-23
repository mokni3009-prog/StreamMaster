package com.streammaster.tv.data.backup

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * §4.42 — encrypted backup + PIN recovery code (2026-11-23).
 *
 * Backup encryption: AES-256/GCM, key derived from a user-chosen password
 * with PBKDF2-HMAC-SHA256 (65 536 iterations, 128-bit random salt, 96-bit
 * random IV per file). The password is NEVER stored — only the salt/IV
 * travel with the file, inside a small JSON envelope:
 *
 *   {"v":1,"salt":"<base64>","iv":"<base64>","data":"<base64 ciphertext>"}
 *
 * A plain (unencrypted) backup is still plain JSON starting with "{" and
 * without the [MAGIC] marker, so old files keep importing
 * ([BackupManager.import] detects the format with [isEncrypted]).
 *
 * PIN recovery: a 6-digit code issued once when a PIN is set. Only its
 * SHA-256 hex is stored (AppSettings.pinRecoveryHash); the code itself is
 * shown once and never persisted. [PinRecovery.verify] compares hashes in
 * constant time.
 */
object BackupCrypto {

    /** Envelope marker: encrypted files start with this JSON key. */
    const val MAGIC = "\"smbk\":1"

    private const val PBKDF2_ITERATIONS = 65_536
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    fun isEncrypted(text: String): Boolean =
        text.trimStart().contains(MAGIC)

    /** Encrypts [plainJson] under [password]. Throws on empty password. */
    fun encrypt(plainJson: String, password: String): String {
        require(password.isNotEmpty()) { "backup password must not be empty" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        val b64 = { bytes: ByteArray -> Base64.encodeToString(bytes, Base64.NO_WRAP) }
        return """{"smbk":1,"salt":"${b64(salt)}","iv":"${b64(iv)}","data":"${b64(cipherText)}"}"""
    }

    /**
     * Decrypts an envelope produced by [encrypt].
     * @throws IllegalArgumentException on a wrong password, a corrupt file
     * or a malformed envelope (GCM authentication failure included).
     */
    fun decrypt(encrypted: String, password: String): String {
        val salt = extractField(encrypted, "salt")
            ?: throw IllegalArgumentException("missing salt")
        val iv = extractField(encrypted, "iv")
            ?: throw IllegalArgumentException("missing iv")
        val data = extractField(encrypted, "data")
            ?: throw IllegalArgumentException("missing data")
        val decode = { s: String -> Base64.decode(s, Base64.NO_WRAP) }
        return try {
            val key = deriveKey(password, decode(salt))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key,
                GCMParameterSpec(GCM_TAG_BITS, decode(iv))
            )
            String(cipher.doFinal(decode(data)), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            // Wrong password and corrupt file both surface here (GCM tag
            // mismatch): report one generic failure, never the cause.
            throw IllegalArgumentException("wrong password or corrupt backup", e)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /** Minimal `"key":"value"` extraction for our own fixed envelope. */
    private fun extractField(json: String, key: String): String? {
        val needle = "\"$key\":\""
        val start = json.indexOf(needle)
        if (start < 0) return null
        val valueStart = start + needle.length
        val end = json.indexOf('"', valueStart)
        if (end < 0) return null
        return json.substring(valueStart, end)
    }

    /** SHA-256 hex of [input] (UTF-8). Used for the PIN recovery hash. */
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

/** §4.42 — PIN recovery code: 6 digits, issued once at PIN setup. */
object PinRecovery {

    private val random = SecureRandom()

    /** A 6-digit code ("000000".."999999", leading zeros kept). */
    fun generateRecoveryCode(): String =
        String.format("%06d", random.nextInt(1_000_000))

    /** Hash stored in AppSettings.pinRecoveryHash — the code is never stored. */
    fun hash(code: String): String = BackupCrypto.sha256Hex(code.trim())

    /** Constant-time comparison of a candidate code against a stored hash. */
    fun verify(code: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        val candidate = hash(code)
        if (candidate.length != storedHash.length) return false
        var diff = 0
        for (i in candidate.indices) diff = diff or (candidate[i].code xor storedHash[i].code)
        return diff == 0
    }
}
