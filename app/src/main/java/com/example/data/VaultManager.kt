package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages encrypted storage for sandboxed application data, credentials, and notes
 * using the hardware-backed Android KeyStore and AES-256-GCM.
 */
class VaultManager(private val context: Context) {

    private val keyAlias = "IsolateBox_Master_Vault_Key"
    private val transformation = "AES/GCM/NoPadding"

    private val keyStore: KeyStore? by lazy {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        } catch (e: Exception) {
            null
        }
    }

    private fun ensureKeyCreated() {
        val ks = keyStore ?: return
        try {
            if (!ks.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val spec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSecretKey(): SecretKey? {
        val ks = keyStore ?: return null
        return try {
            ensureKeyCreated()
            (ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encrypts plaintext and stores it in an isolated private vault file.
     */
    fun storeEncryptedData(fileName: String, plainText: String): Boolean {
        val key = getSecretKey() ?: return false
        return try {
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Prepend 12-byte IV to the ciphertext
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            val vaultDir = File(context.filesDir, "encrypted_vault")
            if (!vaultDir.exists()) vaultDir.mkdirs()

            val targetFile = File(vaultDir, "$fileName.enc")
            targetFile.writeBytes(combined)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reads and decrypts an isolated private vault file.
     */
    fun readEncryptedData(fileName: String): String? {
        val key = getSecretKey() ?: return null
        return try {
            val vaultDir = File(context.filesDir, "encrypted_vault")
            val targetFile = File(vaultDir, "$fileName.enc")
            if (!targetFile.exists()) return null

            val combined = targetFile.readBytes()
            if (combined.size < 12) return null

            val iv = combined.copyOfRange(0, 12)
            val cipherText = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listVaultItems(): List<VaultItem> {
        val vaultDir = File(context.filesDir, "encrypted_vault")
        if (!vaultDir.exists()) return emptyList()

        return vaultDir.listFiles { file -> file.name.endsWith(".enc") }
            ?.map { file ->
                val name = file.name.removeSuffix(".enc")
                VaultItem(
                    name = name,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            } ?: emptyList()
    }

    fun deleteVaultItem(fileName: String): Boolean {
        val vaultDir = File(context.filesDir, "encrypted_vault")
        val targetFile = File(vaultDir, "$fileName.enc")
        return if (targetFile.exists()) targetFile.delete() else false
    }

    data class VaultItem(
        val name: String,
        val sizeBytes: Long,
        val lastModified: Long
    )
}
