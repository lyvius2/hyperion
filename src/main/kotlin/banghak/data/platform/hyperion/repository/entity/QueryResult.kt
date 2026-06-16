package banghak.data.platform.hyperion.repository.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * A query result record created for each NL-to-SQL request.
 *
 * DB records are NEVER physically deleted — use unused='Y' to hide from board.
 * Physical files are deleted by the scheduler after expiry (expiresAt = requestedAt + 2 days).
 */
@Entity
@Table(
    name = "query_results",
    indexes = [
        Index(name = "idx_query_results_system_requested", columnList = "system_id, requested_at DESC"),
        Index(name = "idx_query_results_expires_file_deleted", columnList = "expires_at, file_deleted")
    ]
)
data class QueryResult(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", nullable = false)
    val system: TargetSystem,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    val requestedBy: Member,

    /** LLM-generated dataset title displayed on the board (max 30 chars recommended). */
    @Column(name = "dataset_name", nullable = false, length = 200)
    val datasetName: String,

    @Column(name = "natural_language", nullable = false, columnDefinition = "TEXT")
    val naturalLanguage: String,

    /** LLM-generated SQL kept for audit purposes. */
    @Column(name = "generated_sql", columnDefinition = "TEXT")
    val generatedSql: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 20)
    val resultType: ResultType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: ResultStatus = ResultStatus.PROCESSING,

    /** SQL execution result stored as JSON. */
    @Column(name = "sql_result", columnDefinition = "JSON")
    val sqlResult: String? = null,

    /** Absolute path to result.xlsx or visualization.html on the server. */
    @Column(name = "file_path", length = 500)
    val filePath: String? = null,

    /** d3.js visualization HTML markup (VISUALIZE type only). */
    @Column(name = "graph_markup", columnDefinition = "MEDIUMTEXT")
    val graphMarkup: String? = null,

    /** LLM analysis summary text. */
    @Column(name = "analysis_result", columnDefinition = "TEXT")
    val analysisResult: String? = null,

    /** requestedAt + 2 days. Physical file is deleted by scheduler after this time. */
    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    /** Soft-delete flag — 'Y' hides the record from the board. DB row is never deleted. */
    @Column(nullable = false, length = 1)
    val unused: String = "N",

    /** 'Y' once the physical file has been deleted by the scheduler. */
    @Column(name = "file_deleted", nullable = false, length = 1)
    val fileDeleted: String = "N",

    @Column(name = "file_deleted_at")
    val fileDeletedAt: LocalDateTime? = null,

    /** 'Y' once the Slack Incoming Webhook notification has been sent. */
    @Column(name = "slack_sent", nullable = false, length = 1)
    val slackSent: String = "N",

    @Column(name = "slack_sent_at")
    val slackSentAt: LocalDateTime? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Column(name = "requested_at", nullable = false, updatable = false)
    val requestedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

