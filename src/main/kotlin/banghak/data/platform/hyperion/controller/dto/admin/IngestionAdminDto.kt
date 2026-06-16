package banghak.data.platform.hyperion.controller.dto.admin

import banghak.data.platform.hyperion.repository.entity.FileType
import banghak.data.platform.hyperion.repository.entity.IngestionStatus
import banghak.data.platform.hyperion.dto.IngestionMode
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

// ──────────────────────────────────────────────────────────────────
// Request DTOs
// ──────────────────────────────────────────────────────────────────

/** POST /admin/systems/{systemId}/ingest */
data class TriggerIngestionRequest(
    @field:NotNull
    val mode: IngestionMode
)

// ──────────────────────────────────────────────────────────────────
// Shared embedded DTOs
// ──────────────────────────────────────────────────────────────────

/**
 * Per-file embedding stats in the ingestion status response.
 * fileId is null for the git source-tree chunk group.
 */
data class FileStatResponse(
    val fileId: Long?,
    val filename: String,
    val fileType: String,
    val embeddedChunkCount: Int,
    val lastEmbeddedAt: OffsetDateTime?,
    val isEmbedded: Boolean
)

// ──────────────────────────────────────────────────────────────────
// Response DTOs
// ──────────────────────────────────────────────────────────────────

/** POST /admin/systems/{systemId}/ingest — 202 response data. */
data class TriggerIngestionResponse(
    val jobId: Long,
    val mode: IngestionMode,
    val message: String = "Ingestion started.",
    val statusUrl: String
)

/** GET /admin/systems/{systemId}/ingest/status — 200 response data. */
data class IngestionStatusResponse(
    val systemId: Long,
    val systemName: String,
    val ingestionStatus: IngestionStatus,
    val lastIngestedAt: OffsetDateTime?,
    val totalChunkCount: Int,
    val fileStats: List<FileStatResponse>
)

