package com.ylowstone.tv.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Modul kriptografi & verifikasi keamanan YlowstoneTV v2.4.7:
 * 1. Verifikasi password Admin via Salted SHA-256 (zero plaintext di kode/DEX).
 * 2. Enkripsi Backup & Restore lintas-perangkat dengan AES-256-GCM.
 * 3. Enkripsi kredensial playlist SQLite lokal menggunakan Android Keystore AES-256-GCM.
 */
object SecurityCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val LOCAL_KEY_ALIAS = "ylowstone_tv_local_vault_v1"
    private const val LOCAL_PREFIX = "ylenc:"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    private const val ADMIN_SALT = "YlowstoneTV::AdminSalt::v247"
    private const val ADMIN_HASH_HEX = "774b5619a30f80797b97d2f04d5f07c997f11fe9b2f68805a51a8e093a92d0a2"

    fun verifyAdminPassword(input: String): Boolean {
        val candidate = input.trim()
        if (candidate.isEmpty()) return false
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((ADMIN_SALT + candidate).toByteArray(Charsets.UTF_8))
        val expected = hexToBytes(ADMIN_HASH_HEX)
        return MessageDigest.isEqual(digest, expected)
    }

    private fun deriveBackupKey(): SecretKey {
        val seed = listOf("YlowstoneTV", "BackupVault", "AES256GCM", "v247", "K9x#mP2q").joinToString("::")
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encryptBackupPayload(plainJson: String): Pair<String, String> {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveBackupKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val payloadB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return ivB64 to payloadB64
    }

    fun decryptBackupPayload(ivBase64: String, payloadBase64: String): String {
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val encrypted = Base64.decode(payloadBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveBackupKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = cipher.doFinal(encrypted)
        return String(plain, Charsets.UTF_8)
    }

    private fun getOrCreateLocalKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(LOCAL_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            LOCAL_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    fun encryptLocalField(plain: String): String {
        if (plain.isBlank() || plain.startsWith(LOCAL_PREFIX)) return plain
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateLocalKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            "$LOCAL_PREFIX$ivB64:$dataB64"
        }.getOrDefault(plain)
    }

    fun decryptLocalField(stored: String): String {
        if (stored.isBlank() || !stored.startsWith(LOCAL_PREFIX)) return stored
        return runCatching {
            val body = stored.removePrefix(LOCAL_PREFIX)
            val sep = body.indexOf(':')
            if (sep <= 0) return stored
            val iv = Base64.decode(body.substring(0, sep), Base64.NO_WRAP)
            val data = Base64.decode(body.substring(sep + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateLocalKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            out[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}
