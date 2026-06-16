package banghak.data.platform.hyperion.dto

/**
 * EXPLAIN cost analysis verdict used to decide how to handle the LLM-generated SQL.
 *
 * PASS   — cost is within acceptable range; execute immediately.
 * WARN   — cost exceeds the warn threshold; re-query the LLM for a lighter query.
 * REJECT — cost exceeds the reject threshold; block execution and throw QueryTooExpensiveException.
 */
enum class ExplainVerdict {
    PASS,
    WARN,
    REJECT
}

/**
 * Result of running EXPLAIN on the LLM-generated SQL against the PROD DB.
 *
 * estimatedCost — optimizer cost estimate (from EXPLAIN FORMAT=JSON)
 * estimatedRows — estimated row count
 * accessType    — dominant access type (e.g. "ALL", "ref", "range")
 * verdict       — PASS | WARN | REJECT
 * rawExplain    — full raw EXPLAIN output (for logging / audit)
 */
data class ExplainResult(
    val estimatedCost: Double,
    val estimatedRows: Long,
    val accessType: String,
    val verdict: ExplainVerdict,
    val rawExplain: String
)

