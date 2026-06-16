package banghak.data.platform.hyperion.controller.dto.admin

import banghak.data.platform.hyperion.repository.entity.JobStatus
import banghak.data.platform.hyperion.repository.entity.JobType
import java.time.OffsetDateTime

// ──────────────────────────────────────────────────────────────────
// Shared embedded DTOs
// ──────────────────────────────────────────────────────────────────

/** Minimal member info for "triggeredBy" in job history responses. Null = scheduler. */
data class TriggeredByInfo(
    val id: Long,
    val displayName: String
)

// ──────────────────────────────────────────────────────────────────
// Response DTOs
// ──────────────────────────────────────────────────────────────────

/**
 * GET /admin/jobs — paginated list item.
 * stackTrace is excluded; use detail endpoint to retrieve it.
 */
data class JobHistoryListItemResponse(
    val id: Long,
    val jobType: JobType,
    val systemName: String?,
    val triggeredBy: TriggeredByInfo?,
    val status: JobStatus,
    val startedAt: OffsetDateTime,
    val finishedAt: OffsetDateTime?,
    val durationMs: Long?,
    val inputSummary: String?,
    val outputSummary: String?,
    val errorCode: String?,
    val errorMessage: String?
)

/**
 * GET /admin/jobs/{id} — full detail including stackTrace.
 */
data class JobHistoryDetailResponse(
    val id: Long,
    val jobType: JobType,
    val referenceId: Long?,
    val referenceType: String?,
    val systemName: String?,
    val triggeredBy: TriggeredByInfo?,
    val status: JobStatus,
    val startedAt: OffsetDateTime,
    val finishedAt: OffsetDateTime?,
    val durationMs: Long?,
    val inputSummary: String?,
    val outputSummary: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val stackTrace: String?
)

