package banghak.data.platform.hyperion.repository.entity

import banghak.data.platform.hyperion.controller.dto.admin.CreateSystemRequest
import banghak.data.platform.hyperion.controller.dto.admin.UpdateSystemRequest
import banghak.data.platform.hyperion.dto.EncryptedSystemInfo
import banghak.data.platform.hyperion.infra.crypto.TokenEncryptor
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * An analysis target system registered by an admin.
 *
 * - DB credentials are AES-256-GCM encrypted before storage; decrypt at runtime when needed.
 * - Git access token is AES-256-GCM encrypted.
 * - chromaCollection is the isolated ChromaDB collection name for this system (format: sys_{name}_{hash}).
 * - rootPath is the server-side directory for uploaded/cloned files.
 */
@Entity
@Table(
    name = "target_systems",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_target_systems_name", columnNames = ["name"]),
        UniqueConstraint(name = "uq_target_systems_chroma_collection", columnNames = ["chroma_collection"])
    ]
)
data class TargetSystem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** URL-safe system identifier (alphanumeric + hyphen). UNIQUE. */
    @Column(nullable = false, length = 100)
    val name: String,

    @Column(length = 500)
    val description: String? = null,

    /** Server-side root directory: /data/systems/{name}_{hash}/ */
    @Column(name = "root_path", nullable = false, length = 500)
    val rootPath: String,

    /** ChromaDB collection name: sys_{name}_{hash}. Must never be shared across systems. */
    @Column(name = "chroma_collection", nullable = false, length = 100)
    val chromaCollection: String,

    @Column(name = "db_url", nullable = false, length = 500)
    val dbUrl: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "db_type", nullable = false, length = 20)
    val dbType: DbType,

    /** AES-256-GCM encrypted DB login ID. */
    @Column(name = "db_username_enc", nullable = false, length = 500)
    val dbUsernameEnc: String,

    /** AES-256-GCM encrypted DB password. */
    @Column(name = "db_password_enc", nullable = false, length = 500)
    val dbPasswordEnc: String,

    @Column(name = "git_url", length = 500)
    val gitUrl: String? = null,

    /** AES-256-GCM encrypted Git access token. */
    @Column(name = "git_access_token_enc", length = 500)
    val gitAccessTokenEnc: String? = null,

    @Column(name = "last_git_sync_at")
    val lastGitSyncAt: LocalDateTime? = null,

    /** Last known Git commit hash (SHA-1, 40 chars). */
    @Column(name = "last_commit_hash", length = 40)
    val lastCommitHash: String? = null,

    @Column(name = "slack_webhook_url", length = 500)
    val slackWebhookUrl: String? = null,

    /** Slack notification toggle: 'Y' or 'N'. */
    @Column(name = "slack_enabled", nullable = false, length = 1)
    val slackEnabled: String = "N",

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_status", nullable = false, length = 20)
    val ingestionStatus: IngestionStatus = IngestionStatus.NONE,

    @Column(name = "last_ingested_at")
    val lastIngestedAt: LocalDateTime? = null,

    @Column(name = "total_chunk_count", nullable = false)
    val totalChunkCount: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    val createdBy: Member,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        @JvmStatic
        fun of(request: CreateSystemRequest, createdBy: Member, rootPath: String, chromaCollection: String, encrypted: EncryptedSystemInfo): TargetSystem {
            val now = LocalDateTime.now()
            return TargetSystem(
                name = request.name,
                description = request.description,
                rootPath = rootPath,
                chromaCollection = chromaCollection,
                dbUrl = request.dbUrl,
                dbType = request.dbType,
                dbUsernameEnc = encrypted.dbUsernameEnc,
                dbPasswordEnc = encrypted.dbPasswordEnc,
                gitUrl = request.gitUrl,
                gitAccessTokenEnc = encrypted.gitAccessTokenEnc,
                createdBy = createdBy,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    fun copy(request: UpdateSystemRequest, encrypted: EncryptedSystemInfo): TargetSystem {
        return this.copy(
            description = request.description ?: this.description,
            dbUrl = request.dbUrl ?: this.dbUrl,
            dbType = request.dbType ?: this.dbType,
            dbUsernameEnc = encrypted.dbUsernameEnc,
            dbPasswordEnc = encrypted.dbPasswordEnc,
            gitUrl = request.gitUrl ?: this.gitUrl,
            gitAccessTokenEnc = encrypted.gitAccessTokenEnc,
            slackWebhookUrl = request.slackWebhookUrl ?: this.slackWebhookUrl,
            slackEnabled = request.slackEnabled ?: this.slackEnabled,
            updatedAt = LocalDateTime.now()
        )
    }
}

