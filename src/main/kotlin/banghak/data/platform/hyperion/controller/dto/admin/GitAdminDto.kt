package banghak.data.platform.hyperion.controller.dto.admin

import java.time.OffsetDateTime

// ──────────────────────────────────────────────────────────────────
// Response DTOs
// ──────────────────────────────────────────────────────────────────

/** POST /admin/systems/{systemId}/git/sync — 202 response data. */
data class GitSyncResponse(
    val jobId: Long,
    val message: String = "Git synchronization started.",
    val statusUrl: String
)

/** GET /admin/systems/{systemId}/git/status — 200 response data. */
data class GitStatusResponse(
    val gitUrl: String?,
    val lastSyncAt: OffsetDateTime?,
    val lastCommitHash: String?,
    val lastCommitMessage: String?,
    /** true if the local source-tree directory exists for this system. */
    val sourcetreeExists: Boolean,
    /** Latest Git job status: SUCCESS | FAILED | RUNNING | null (never synced). */
    val syncStatus: String?
)

