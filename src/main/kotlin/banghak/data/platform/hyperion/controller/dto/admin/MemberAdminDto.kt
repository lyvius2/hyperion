package banghak.data.platform.hyperion.controller.dto.admin

import banghak.data.platform.hyperion.repository.entity.MemberRole
import banghak.data.platform.hyperion.repository.entity.MemberStatus
import jakarta.validation.constraints.*
import java.time.OffsetDateTime

// ──────────────────────────────────────────────────────────────────
// Request DTOs
// ──────────────────────────────────────────────────────────────────

/** POST /admin/members */
data class CreateMemberRequest(
    @field:NotBlank
    @field:Size(min = 5, max = 50)
    @field:Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username may only contain alphanumeric characters and underscores.")
    val username: String,

    @field:NotBlank
    @field:Email
    @field:Size(max = 200)
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 255)
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).+$",
        message = "Password must contain uppercase, lowercase, digit, and special character."
    )
    val password: String,

    @field:NotBlank
    @field:Size(min = 2, max = 100)
    val displayName: String,

    @field:NotNull
    val role: MemberRole
)

/** PUT /admin/members/{id}/role */
data class ChangeMemberRoleRequest(
    @field:NotNull
    val role: MemberRole
)

/** PUT /admin/members/{id}/status */
data class ChangeMemberStatusRequest(
    @field:NotNull
    val status: MemberStatus,

    val reason: String? = null
)

// ──────────────────────────────────────────────────────────────────
// Response DTOs
// ──────────────────────────────────────────────────────────────────

/** Used in both list (GET /admin/members) and create (POST /admin/members) responses. */
data class MemberResponse(
    val id: Long,
    val username: String,
    val email: String,
    val displayName: String,
    val role: MemberRole,
    val status: MemberStatus,
    val lastLoginAt: OffsetDateTime?,
    val createdAt: OffsetDateTime
)

/** PUT /admin/members/{id}/role — minimal confirmation. */
data class MemberRoleUpdatedResponse(
    val id: Long,
    val role: MemberRole
)

/** PUT /admin/members/{id}/status — minimal confirmation. */
data class MemberStatusUpdatedResponse(
    val id: Long,
    val status: MemberStatus
)

