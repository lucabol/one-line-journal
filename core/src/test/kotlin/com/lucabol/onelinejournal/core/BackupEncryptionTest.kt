package com.lucabol.onelinejournal.core

import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class BackupEncryptionTest {
    private val password = "a long recovery phrase".toCharArray()

    @Test fun `encrypted backups restore independently and never contain entry plaintext`() {
        val original = snapshot(listOf(entry("2026-10-19", "A uniquely private memory")))
        val encrypted = BackupEncryption.encode(original, password)
        assertTrue(BackupEncryption.isEncrypted(encrypted))
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("A uniquely private memory"))
        assertEquals(original, BackupEncryption.decode(encrypted, password.copyOf()))
    }

    @Test fun `random salt and nonce make repeated encryption different`() {
        val original = snapshot()
        val first = BackupEncryption.encode(original, password)
        val second = BackupEncryption.encode(original, password)
        assertFalse(first.contentEquals(second))
        assertEquals(original, BackupEncryption.decode(second, password))
    }

    @Test fun `wrong missing and short passphrases fail explicitly`() {
        val encrypted = BackupEncryption.encode(snapshot(), password)
        assertFails { BackupEncryption.decode(encrypted, "incorrect password".toCharArray()) }
        assertFails { BackupEncryption.decode(encrypted, null) }
        assertFails { BackupEncryption.encode(snapshot(), "short".toCharArray()) }
    }

    @Test fun `tampering and excessive key derivation cost are rejected`() {
        val encrypted = BackupEncryption.encode(snapshot(), password).toString(Charsets.UTF_8)
        assertFails {
            BackupEncryption.decode(encrypted.replace("\"iterations\": 600000", "\"iterations\": 999999999").toByteArray(), password)
        }
        val changed = encrypted.replace(Regex("(\"ciphertext\": \")[A-Za-z0-9]"), "$1!")
        assertFails { BackupEncryption.decode(changed.toByteArray(), password) }
    }

    @Test fun `unencrypted export uses exactly the portable journal format`() {
        val original = snapshot()
        val bytes = BackupEncryption.encode(original, null)
        assertFalse(BackupEncryption.isEncrypted(bytes))
        assertEquals(original, BackupEncryption.decode(bytes, null))
    }
}
