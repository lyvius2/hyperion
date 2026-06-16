package banghak.data.platform.hyperion.dto

import banghak.data.platform.hyperion.repository.entity.ResultType

/**
 * WebSocket messages sent from the server to the client over STOMP.
 *
 * All message types share a common `type` discriminator field so the client
 * can deserialise with a single switch/when on `type`.
 */
sealed class WebSocketMessage {
    abstract val type: String
}

/**
 * Intermediate progress update sent during async query processing.
 *
 * step values (in order):
 *   "Generating SQL..."    — after LLM prompt is dispatched
 *   "Validating query..."  — during EXPLAIN execution
 *   "Querying data..."     — during PROD DB SELECT
 *   "Generating file..."   — during Excel / HTML generation
 */
data class ProgressMessage(
    override val type: String = "PROGRESS",
    val resultId: Long,
    val step: String,
    val stepIndex: Int,
    val totalSteps: Int = 4
) : WebSocketMessage()

/**
 * Final success message. Instructs the client to navigate to the board URL.
 */
data class BoardReadyMessage(
    override val type: String = "BOARD_READY",
    val resultId: Long,
    val resultType: ResultType,
    val datasetName: String,
    val boardUrl: String
) : WebSocketMessage()

/**
 * Error message sent when async processing fails.
 *
 * errorCode values:
 *   NOT_DATA_EXTRACTION    — LLM rejected the request as non-data-extraction
 *   QUERY_TOO_EXPENSIVE    — EXPLAIN cost threshold exceeded
 *   SQL_GENERATION_FAILED  — LLM failed to produce valid SQL
 *   DB_EXECUTION_FAILED    — PROD DB execution error
 *   FILE_GENERATION_FAILED — Excel/HTML generation error
 */
data class ErrorMessage(
    override val type: String = "ERROR",
    val resultId: Long,
    val errorCode: String,
    val message: String
) : WebSocketMessage()

