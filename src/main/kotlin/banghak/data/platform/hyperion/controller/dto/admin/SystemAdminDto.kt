package banghak.data.platform.hyperion.controller.dto.admin

import banghak.data.platform.hyperion.repository.entity.DbType
import banghak.data.platform.hyperion.repository.entity.FileType
import banghak.data.platform.hyperion.repository.entity.IngestionStatus
import jakarta.validation.constraints.*
import java.time.OffsetDateTime

// ──────────────────────────────────────────────────────────────────
// Request DTOs
// ──────────────────────────────────────────────────────────────────

/** POST /admin/systems */
data class CreateSystemRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 100)
    @field:Pattern(
        regexp = "^[a-zA-Z0-9-]+$",
        message = "System name may only contain alphanumeric characters and hyphens."
    )
    val name: String,

    @field:Size(max = 500)
    val description: String? = null,

    @field:Size(max = 500)
    val dbUrl: String,

    val dbType: DbType,

    @field:Size(max = 255)
    val dbUsername: String,

    @field:Size(max = 255)
    val dbPassword: String,

    @field:Size(max = 500)
    val gitUrl: String? = null,

    @field:Size(max = 255)
    val gitAccessToken: String? = null,

    @field:Size(max = 500)
    val slackWebhookUrl: String? = null,

    @field:Pattern(regexp = "^[YN]$", message = "slackEnabled must be 'Y' or 'N'.")
    val slackEnabled: String = "N"
)

/**
 * PUT /admin/systems/{id} — partial update, only provided fields are changed.
 * All fields are nullable; null means "no change".
 */
data class UpdateSystemRequest(
    @field:Size(max = 500)
    val description: String? = null,

    @field:Size(max = 500)
    val dbUrl: String? = null,

    val dbType: DbType? = null,

    @field:Size(max = 255)
    val dbUsername: String? = null,

    @field:Size(max = 255)
    val dbPassword: String? = null,

    @field:Size(max = 500)
    val gitUrl: String? = null,

    @field:Size(max = 255)
    val gitAccessToken: String? = null,

    @field:Size(max = 500)
    val slackWebhookUrl: String? = null,

    @field:Pattern(regexp = "^[YN]$", message = "slackEnabled must be 'Y' or 'N'.")
    val slackEnabled: String? = null
)

// ──────────────────────────────────────────────────────────────────
// Shared embedded DTOs
// ──────────────────────────────────────────────────────────────────

/** Minimal member info for "createdBy" fields in system responses. */
data class CreatedByInfo(
    val id: Long,
    val displayName: String
)

/** Condensed file info embedded in SystemDetailResponse.files. */
data class SystemFileInfo(
    val id: Long,
    val originalFilename: String,
    val fileType: FileType,
    val fileSize: Long,
    val embeddedChunkCount: Int,
    val lastEmbeddedAt: OffsetDateTime?
)

// ──────────────────────────────────────────────────────────────────
// Response DTOs
// ──────────────────────────────────────────────────────────────────

/** GET /admin/systems — list item. dbPassword is never included. */
data class SystemListItemResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val dbType: DbType,
    val dbUrl: String,
    /** Masked, e.g. "he***r". */
    val dbUsername: String,
    val ingestionStatus: IngestionStatus,
    val lastIngestedAt: OffsetDateTime?,
    val totalChunkCount: Int,
    val gitUrl: String?,
    val lastGitSyncAt: OffsetDateTime?,
    val slackEnabled: String,
    val fileCount: Int,
    val createdAt: OffsetDateTime
)

/** GET /admin/systems/{id} — full detail. */
data class SystemDetailResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val rootPath: String,
    val chromaCollection: String,
    val dbType: DbType,
    val dbUrl: String,
    /** Masked, e.g. "he***r". */
    val dbUsername: String,
    val gitUrl: String?,
    val lastGitSyncAt: OffsetDateTime?,
    val lastCommitHash: String?,
    val slackEnabled: String,
    val slackWebhookUrl: String?,
    val ingestionStatus: IngestionStatus,
    val lastIngestedAt: OffsetDateTime?,
    val totalChunkCount: Int,
    val files: List<SystemFileInfo>,
    val createdBy: CreatedByInfo,
    val createdAt: OffsetDateTime
)

/** POST /admin/systems — 201 response data. */
data class SystemCreatedResponse(
    val id: Long,
    val name: String,
    val rootPath: String,
    val chromaCollection: String,
    val ingestionStatus: IngestionStatus,
    val createdAt: OffsetDateTime
)

/** PUT /admin/systems/{id} — 200 confirmation. */
data class SystemUpdatedResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val updatedAt: OffsetDateTime
)

