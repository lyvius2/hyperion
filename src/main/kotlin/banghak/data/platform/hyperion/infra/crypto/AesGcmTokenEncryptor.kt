package banghak.data.platform.hyperion.infra.crypto

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM implementation of [TokenEncryptor].
 *
 * Ciphertext format (Base64): `IV (12 bytes) || ciphertext+tag`.
 * Each encryption uses a fresh random IV from [SecureRandom].
 */
@Component
class AesGcmTokenEncryptor(properties: CryptoProperties) : TokenEncryptor {

    private val keySpec: SecretKeySpec = run {
        val keyBytes = Base64.getDecoder().decode(properties.masterKey)
        require(keyBytes.size == KEY_BYTES) {
            "hyperion.crypto.master-key must decode to $KEY_BYTES bytes (256-bit AES key)."
        }
        SecretKeySpec(keyBytes, "AES")
    }
    private val random = SecureRandom()

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, iv))
        }
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherBytes.size).also {
            System.arraycopy(iv, 0, it, 0, iv.size)
            System.arraycopy(cipherBytes, 0, it, iv.size, cipherBytes.size)
        }
        return Base64.getEncoder().encodeToString(combined)
    }

    override fun decrypt(ciphertext: String): String {
        val combined = Base64.getDecoder().decode(ciphertext)
        require(combined.size > IV_BYTES) { "Ciphertext is too short to contain an IV." }
        val iv = combined.copyOfRange(0, IV_BYTES)
        val body = combined.copyOfRange(IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, iv))
        }
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}