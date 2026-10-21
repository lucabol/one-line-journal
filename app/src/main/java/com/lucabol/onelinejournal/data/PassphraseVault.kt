package com.lucabol.onelinejournal.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PassphraseVault(context: Context) {
    private val preferences = context.getSharedPreferences("local_encryption", Context.MODE_PRIVATE)
    private val alias = "one-line-backup-passphrase"

    fun save(passphrase: CharArray) {
        require(passphrase.size >= 12) { "Use at least 12 characters for your recovery passphrase." }
        val plain = String(passphrase).toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val packed = cipher.iv + cipher.doFinal(plain)
            check(preferences.edit().putString("wrapped", Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
                "Could not protect the recovery passphrase on this phone."
            }
        } finally { plain.fill(0) }
    }

    fun load(): CharArray {
        val encoded = preferences.getString("wrapped", null)
            ?: error("The backup recovery passphrase is missing. Set it again in Privacy before backing up.")
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size >= 28) { "The protected local passphrase is damaged. Re-enter it in Privacy." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        val plain = cipher.doFinal(packed.copyOfRange(12, packed.size))
        return try { plain.toString(Charsets.UTF_8).toCharArray() } finally { plain.fill(0) }
    }

    fun clear() {
        check(preferences.edit().remove("wrapped").commit()) { "Could not clear the local passphrase." }
    }

    private fun key(create: Boolean = true): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "The phone's encryption key is missing. Re-enter your recovery passphrase in Privacy." }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setKeySize(256)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }
}
