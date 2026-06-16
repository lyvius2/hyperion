package banghak.data.platform.hyperion.repository.entity

enum class IngestionStatus {
    /** No embedding has been triggered yet (initial state). */
    NONE,

    /** Embedding pipeline is currently running. */
    RUNNING,

    /** Embedding pipeline completed successfully. */
    COMPLETED,

    /** Embedding pipeline failed. */
    FAILED
}

