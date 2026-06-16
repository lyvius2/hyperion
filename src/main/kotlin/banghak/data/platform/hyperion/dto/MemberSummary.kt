package banghak.data.platform.hyperion.dto

/**
 * Minimal member summary used internally across service and facade layers.
 * Not exposed directly as an HTTP response — controller DTOs use their own embedded types.
 */
data class MemberSummary(
    val id: Long,
    val username: String,
    val displayName: String
)

