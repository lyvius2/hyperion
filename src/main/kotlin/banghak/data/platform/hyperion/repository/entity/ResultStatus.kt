package banghak.data.platform.hyperion.repository.entity

enum class ResultStatus {
    /** Async processing is in progress. */
    PROCESSING,

    /** Processing completed successfully. */
    COMPLETED,

    /** Processing failed. */
    FAILED
}

