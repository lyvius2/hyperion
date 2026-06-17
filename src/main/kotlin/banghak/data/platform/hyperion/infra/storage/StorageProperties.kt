package banghak.data.platform.hyperion.infra.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Storage roots loaded from `hyperion.storage.*`.
 * - `systemsRoot`: parent directory holding each target system's uploaded files.
 * - `resultsRoot`: parent directory holding generated query result artifacts.
 */
@ConfigurationProperties(prefix = "hyperion.storage")
data class StorageProperties(
    val systemsRoot: String,
    val resultsRoot: String
)