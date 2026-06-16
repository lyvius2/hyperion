package banghak.data.platform.hyperion.controller.dto.admin

import banghak.data.platform.hyperion.repository.entity.FileType
import java.time.OffsetDateTime

// ──────────────────────────────────────────────────────────────────
// Shared embedded DTOs
// ──────────────────────────────────────────────────────────────────

/** Minimal uploader info embedded in file responses. */
data class UploaderInfo(
    val id: Long,
    val displayName: String
)

// ──────────────────────────────────────────────────────────────────
// Response DTOs
// ──────────────────────────────────────────────────────────────────

/**
 * GET /admin/systems/{systemId}/files — list item.
 * Full detail including storedPath and sourceHash is available here (admin only).
 */
data class SystemFileResponse(
    val id: Long,
    val originalFilename: String,
    val storedPath: String,
    val fileType: FileType,
    val fileSize: Long,
    val sourceHash: String?,
    val embeddedChunkCount: Int,
    val lastEmbeddedAt: OffsetDateTime?,
    val uploadedBy: UploaderInfo,
    val uploadedAt: OffsetDateTime
)

/** POST /admin/systems/{systemId}/files — 201 response data. */
data class FileUploadResponse(
    val id: Long,
    val originalFilename: String,
    val storedPath: String,
    val fileType: FileType,
    val fileSize: Long,
    val uploadedAt: OffsetDateTime,
    /** true when background embedding was triggered immediately after upload. */
    val ingestionTriggered: Boolean = true,
    val message: String = "File uploaded successfully. Embedding ingestion has started in the background."
)

