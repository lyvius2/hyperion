package banghak.data.platform.hyperion.dto

/**
 * Ingestion trigger mode.
 *
 * FULL        — delete all existing ChromaDB chunks and re-embed every file.
 * INCREMENTAL — re-embed only files whose SHA-256 hash has changed since the last ingestion.
 */
enum class IngestionMode {
    FULL,
    INCREMENTAL
}

