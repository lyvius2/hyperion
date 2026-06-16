package banghak.data.platform.hyperion.controller.dto.auth

import banghak.data.platform.hyperion.repository.entity.MemberRole
import banghak.data.platform.hyperion.repository.entity.MemberStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

// ──────────────────────────────────────────────────────────────────
// Request DTOs
// ──────────────────────────────────────────────────────────────────

/** POST /auth/login */
data class LoginRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 50)
    val username: String,

    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val password: String
)

/** PUT /auth/password */
data class ChangePasswordRequest(
    @field:NotBlank
    val currentPassword: String,

    @field:NotBlank
    @field:Size(min = 8, max = 255)
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).+$",
        message = "Password must contain uppercase, lowercase, digit, and special character."
    )
    val newPassword: String,

    @field:NotBlank
    val newPasswordConfirm: String
)

// ──────────────────────────────────────────────────────────────────
// Response DTOs
// ──────────────────────────────────────────────────────────────────

/** Embedded member info returned in the login response body. */
data class MemberLoginInfo(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: MemberRole,
    val profileImageUrl: String? = null
)

/** POST /auth/login — 200 response data. */
data class LoginResponse(
    val member: MemberLoginInfo
)

/** GET /auth/me — 200 response data. */
data class MeResponse(
    val id: Long,
    val username: String,
    val email: String,
    val displayName: String,
    val role: MemberRole,
    val status: MemberStatus,
    val lastLoginAt: OffsetDateTime?
)

/** POST /api/auth/heartbeat — 200 response data. */
data class HeartbeatResponse(
    /** Remaining session TTL in seconds after reset. Always 900 (= 15 min). */
    val sessionExpiresIn: Int = 900
)

