package com.lucabol.onelinejournal.core

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BackupLockedException : IllegalArgumentException("This backup is encrypted. Enter its recovery passphrase.")

@Serializable
private data class EncryptedBackup(
    val format: String = "one-line-journal-encrypted",
    val schemaVersion: Int = 1,
    val kdf: String = "PBKDF2-HMAC-SHA256",
    val iterations: Int = 600_000,
    val salt: String,
    val iv: String,
    val ciphertext: String,
)

object BackupEncryption {
    private val random = SecureRandom()
    private val associatedData = "one-line-journal-encrypted-v1".toByteArray()

    fun isEncrypted(bytes: ByteArray): Boolean {
        require(bytes.size <= MAX_BACKUP_BYTES) { "The backup exceeds the 32 MB limit." }
        return JournalFormat.json.parseToJsonElement(JournalFormat.utf8(bytes))
            .jsonObject["format"]?.jsonPrimitive?.content == "one-line-journal-encrypted"
    }

    fun encode(snapshot: JournalSnapshot, passphrase: CharArray?): ByteArray {
        val plain = JournalFormat.encode(snapshot)
        if (passphrase == null) return plain
        require(passphrase.size >= 12) { "Use a recovery passphrase of at least 12 characters." }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = derive(passphrase, salt, 600_000)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(associatedData)
            val envelope = EncryptedBackup(salt = b64(salt), iv = b64(iv), ciphertext = b64(cipher.doFinal(plain)))
            return JournalFormat.json.encodeToString(EncryptedBackup.serializer(), envelope).toByteArray()
                .also { require(it.size <= MAX_BACKUP_BYTES) { "The encrypted backup exceeds the size limit." } }
        } finally {
            key.fill(0)
            plain.fill(0)
        }
    }

    fun decode(bytes: ByteArray, passphrase: CharArray?): JournalSnapshot {
        if (!isEncrypted(bytes)) return JournalFormat.decode(bytes)
        if (passphrase == null) throw BackupLockedException()
        val envelope = JournalFormat.json.decodeFromString(EncryptedBackup.serializer(), JournalFormat.utf8(bytes))
        require(envelope.schemaVersion == 1 && envelope.kdf == "PBKDF2-HMAC-SHA256") { "Unsupported encrypted backup." }
        require(envelope.iterations in 100_000..2_000_000) { "Unsupported encryption work factor." }
        val salt = Base64.getDecoder().decode(envelope.salt)
        val iv = Base64.getDecoder().decode(envelope.iv)
        require(salt.size == 16 && iv.size == 12) { "Invalid encryption parameters." }
        val key = derive(passphrase, salt, envelope.iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(associatedData)
            val plain = cipher.doFinal(Base64.getDecoder().decode(envelope.ciphertext))
            return try { JournalFormat.decode(plain) } finally { plain.fill(0) }
        } finally { key.fill(0) }
    }

    private fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
