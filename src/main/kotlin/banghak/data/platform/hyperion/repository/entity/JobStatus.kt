package banghak.data.platform.hyperion.repository.entity

enum class JobStatus {
    /** Job is executing. */
    RUNNING,

    /** Job completed successfully. */
    SUCCESS,

    /** Job failed. */
    FAILED,

    /** Job was skipped (e.g. no changes detected in incremental ingestion). */
    SKIPPED
}

