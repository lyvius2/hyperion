package banghak.data.platform.hyperion.repository.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Platform member (user account).
 * Password is stored as a BCrypt hash — the original value is never persisted.
 * Authentication sessions are stored in Redis, not here.
 */
@Entity
@Table(
    name = "members",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_members_username", columnNames = ["username"]),
        UniqueConstraint(name = "uq_members_email", columnNames = ["email"])
    ]
)
data class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    val username: String,

    @Column(nullable = false, length = 200)
    val email: String,

    /** BCrypt hash — raw password must never be stored. */
    @Column(name = "password_hash", nullable = false, length = 255)
    val passwordHash: String,

    @Column(name = "display_name", nullable = false, length = 100)
    val displayName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: MemberRole = MemberRole.USER,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: MemberStatus = MemberStatus.ACTIVE,

    @Column(name = "failed_login_count", nullable = false)
    val failedLoginCount: Int = 0,

    /** NULL means not locked. Non-null means locked until this time. */
    @Column(name = "locked_until")
    val lockedUntil: LocalDateTime? = null,

    @Column(name = "password_changed_at")
    val passwordChangedAt: LocalDateTime? = null,

    @Column(name = "last_login_at")
    val lastLoginAt: LocalDateTime? = null,

    /** Supports IPv6 addresses (max 45 chars). */
    @Column(name = "last_login_ip", length = 45)
    val lastLoginIp: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

