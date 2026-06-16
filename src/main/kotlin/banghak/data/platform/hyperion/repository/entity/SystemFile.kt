package banghak.data.platform.hyperion.repository.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * A file (.md or .sql) uploaded to a target system for embedding.
 *
 * - storedPath is relative to the system's rootPath.
 * - sourceHash (SHA-256) is used for incremental change detection.
 * - embeddedChunkCount reflects the number of chunks currently stored in ChromaDB.
 */
@Entity
@Table(name = "system_files")
data class SystemFile(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", nullable = false)
    val system: TargetSystem,

    @Column(name = "original_filename", nullable = false, length = 255)
    val originalFilename: String,

    /** Path relative to the system root directory (e.g. "docs/schema.md"). */
    @Column(name = "stored_path", nullable = false, length = 500)
    val storedPath: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    val fileType: FileType,

    @Column(name = "file_size", nullable = false)
    val fileSize: Long,

    /** SHA-256 hash of file contents — used for incremental embedding detection. */
    @Column(name = "source_hash", length = 64)
    val sourceHash: String? = null,

    @Column(name = "last_embedded_at")
    val lastEmbeddedAt: LocalDateTime? = null,

    @Column(name = "embedded_chunk_count", nullable = false)
    val embeddedChunkCount: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    val uploadedBy: Member,

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    val uploadedAt: LocalDateTime = LocalDateTime.now()
)

