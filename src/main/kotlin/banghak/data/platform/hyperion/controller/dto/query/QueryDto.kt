package banghak.data.platform.hyperion.controller.dto.query

import banghak.data.platform.hyperion.repository.entity.DbType
import banghak.data.platform.hyperion.repository.entity.IngestionStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

// ──────────────────────────────────────────────────────────────────
// Request DTOs
// ──────────────────────────────────────────────────────────────────

/**
 * Shared request body for both POST /api/query/extract and POST /api/query/visualize.
 */
data class QueryRequest(
    @field:NotNull
    @field:Positive
    val systemId: Long,

    @field:NotBlank
    @field:Size(max = 1000, message = "Natural language request must not exceed 1000 characters.")
    val naturalLanguage: String
)

// ──────────────────────────────────────────────────────────────────
// Response DTOs
// ──────────────────────────────────────────────────────────────────

/**
 * GET /api/systems — system summary for the query submission page.
 * DB credentials are intentionally excluded.
 */
data class SystemSummaryResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val ingestionStatus: IngestionStatus,
    val lastIngestedAt: OffsetDateTime?,
    val totalChunkCount: Int,
    val dbType: DbType
)

/**
 * Response body for POST /api/query/extract and POST /api/query/visualize (HTTP 202).
 * The client subscribes to wsSubscribePath via STOMP to receive the async result.
 */
data class QueryAcceptedResponse(
    /** QueryResult.id — usable as /board/{resultId} after completion. */
    val resultId: Long,

    /** STOMP session identifier used for subscription. */
    val sessionId: String,

    val message: String = "Processing started. Subscribe to the WebSocket session ID to receive status updates.",

    /** STOMP topic the client should subscribe to: /topic/session/{sessionId}. */
    val wsSubscribePath: String
)

