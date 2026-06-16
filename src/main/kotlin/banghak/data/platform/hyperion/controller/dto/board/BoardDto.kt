package banghak.data.platform.hyperion.controller.dto.board

import banghak.data.platform.hyperion.repository.entity.ResultStatus
import banghak.data.platform.hyperion.repository.entity.ResultType
import java.time.OffsetDateTime

// ──────────────────────────────────────────────────────────────────
// Shared embedded DTOs
// ──────────────────────────────────────────────────────────────────

/** Minimal member info embedded in board responses. */
data class RequesterInfo(
    val id: Long,
    val displayName: String
)

// ──────────────────────────────────────────────────────────────────
// Response DTOs
// ──────────────────────────────────────────────────────────────────

/**
 * GET /board — single item in the paginated list.
 * Does not include generatedSql or naturalLanguage (detail only).
 */
data class BoardListItemResponse(
    val id: Long,
    val systemName: String,
    val datasetName: String,
    val resultType: ResultType,
    val status: ResultStatus,
    val requestedBy: RequesterInfo,
    val requestedAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
    /** Hours remaining until the result file expires. */
    val expiresInHours: Long,
    val fileAvailable: Boolean
)

/**
 * GET /board/{id} — full detail.
 *
 * - EXTRACT type: downloadUrl is populated, htmlUrl is null.
 * - VISUALIZE type: htmlUrl is populated, downloadUrl is null.
 */
data class BoardDetailResponse(
    val id: Long,
    val systemName: String,
    val datasetName: String,
    val naturalLanguage: String,
    val generatedSql: String?,
    val resultType: ResultType,
    val status: ResultStatus,
    val requestedBy: RequesterInfo,
    val requestedAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
    val expiresInHours: Long,
    val fileAvailable: Boolean,

    /** /board/{id}/download — populated for EXTRACT type only. */
    val downloadUrl: String? = null,

    /** /board/{id}/html — populated for VISUALIZE type only. */
    val htmlUrl: String? = null,

    val errorMessage: String? = null
)

