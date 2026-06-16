package banghak.data.platform.hyperion.repository.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * One-time token for password reset flows.
 *
 * Authentication sessions are stored in Redis (Spring Session), not here.
 * Only PASSWORD_RESET tokens are stored in this table.
 * The original token value is never persisted — only the SHA-256 hash.
 */
@Entity
@Table(name = "member_tokens")
data class MemberToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 30)
    val tokenType: TokenType,

    /** SHA-256 hash of the original token — the raw token is never stored. */
    @Column(name = "token_hash", nullable = false, length = 255)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    /** NULL = not yet used. */
    @Column(name = "used_at")
    val usedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

