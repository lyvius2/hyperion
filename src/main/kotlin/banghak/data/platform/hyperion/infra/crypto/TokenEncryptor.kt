package banghak.data.platform.hyperion.infra.crypto

/**
 * Symmetric encryptor for sensitive credentials (DB passwords, Git tokens, etc.).
 * Implementations must use authenticated encryption (AES-GCM or equivalent).
 */
interface TokenEncryptor {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}