package banghak.data.platform.hyperion.repository.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Immutable audit log for every async job executed on the platform.
 *
 * Records are never deleted — they serve as a permanent audit trail.
 * system and triggeredBy use SET NULL on FK deletion to preserve the history row.
 */
@Entity
@Table(
    name = "job_history",
    indexes = [
        Index(name = "idx_job_history_type_started", columnList = "job_type, started_at DESC"),
        Index(name = "idx_job_history_system_started", columnList = "system_id, started_at DESC"),
        Index(name = "idx_job_history_status_started", columnList = "status, started_at DESC")
    ]
)
data class JobHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    val jobType: JobType,

    /** ID of the associated record (e.g. QueryResult.id, TargetSystem.id). */
    @Column(name = "reference_id")
    val referenceId: Long? = null,

    /** Discriminator for referenceId: QUERY_RESULT | TARGET_SYSTEM | SYSTEM_FILE. */
    @Column(name = "reference_type", length = 50)
    val referenceType: String? = null,

    /**
     * Associated system. ON DELETE SET NULL — row is kept even if the system is deleted.
     * Nullable because the system may be deleted after the job was created.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", foreignKey = ForeignKey(value = ConstraintMode.CONSTRAINT, foreignKeyDefinition = "FOREIGN KEY (system_id) REFERENCES target_systems(id) ON DELETE SET NULL"))
    val system: TargetSystem? = null,

    /**
     * Member who triggered the job. ON DELETE SET NULL — NULL means triggered by the scheduler.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by", foreignKey = ForeignKey(value = ConstraintMode.CONSTRAINT, foreignKeyDefinition = "FOREIGN KEY (triggered_by) REFERENCES members(id) ON DELETE SET NULL"))
    val triggeredBy: Member? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: JobStatus = JobStatus.RUNNING,

    @Column(name = "started_at", nullable = false, updatable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "finished_at")
    val finishedAt: LocalDateTime? = null,

    /** Total execution time in milliseconds. */
    @Column(name = "duration_ms")
    val durationMs: Long? = null,

    /** Brief description of input (e.g. first 200 chars of natural language query). */
    @Column(name = "input_summary", length = 1000)
    val inputSummary: String? = null,

    /** Brief description of output (e.g. "rows=500", "chunks=320"). */
    @Column(name = "output_summary", length = 1000)
    val outputSummary: String? = null,

    /** Exception class name (e.g. "QueryTooExpensiveException"). */
    @Column(name = "error_code", length = 100)
    val errorCode: String? = null,

    @Column(name = "error_message", length = 2000)
    val errorMessage: String? = null,

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    val stackTrace: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

