package banghak.data.platform.hyperion.infra.crypto

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Crypto configuration loaded from `hyperion.crypto.*`.
 * `master-key` must be a Base64-encoded 32-byte value (256-bit AES key).
 */
@ConfigurationProperties(prefix = "hyperion.crypto")
data class CryptoProperties(
    val masterKey: String
)