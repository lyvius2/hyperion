package banghak.data.platform.hyperion.repository.entity

enum class JobType {
    /** Data extraction query execution (NL → SQL → Excel). */
    QUERY_EXTRACT,

    /** Data visualization query execution (NL → SQL → HTML). */
    QUERY_VISUALIZE,

    /** EXPLAIN cost validation against the PROD DB. */
    SQL_EXPLAIN,

    /** Full re-embedding of all files in a system. */
    INGESTION_FULL,

    /** Incremental embedding based on SHA-256 change detection. */
    INGESTION_INCREMENTAL,

    /** Single-file embedding triggered by upload. */
    INGESTION_SINGLE_FILE,

    /** Git clone (first sync). */
    GIT_CLONE,

    /** Git pull (subsequent syncs). */
    GIT_PULL,

    /** Expired result-file deletion (runs via scheduler). */
    FILE_CLEANUP,

    /** Slack Incoming Webhook notification dispatch. */
    SLACK_NOTIFY
}

