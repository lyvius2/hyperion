package banghak.data.platform.hyperion.controller.dto

import java.time.OffsetDateTime

/**
 * Unified API response envelope.
 *
 * Success:   Rest(success=true,  data=..., meta=ApiMeta(...))
 * Paginated: Rest(success=true,  data=..., meta=PageMeta(...))
 * Error:     Rest(success=false, error=ErrorDetail(...), meta=ApiMeta(...))
 */
data class Rest<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorDetail? = null,
    val meta: Any? = null
) {
    companion object {
        fun <T> ok(data: T): Rest<T> =
            Rest(success = true, data = data, meta = ApiMeta())

        fun <T> created(data: T): Rest<T> =
            Rest(success = true, data = data, meta = ApiMeta())

        fun <T> accepted(data: T): Rest<T> =
            Rest(success = true, data = data, meta = ApiMeta())

        fun <T> paged(data: T, page: Int, size: Int, totalElements: Long): Rest<T> =
            Rest(
                success = true,
                data = data,
                meta = PageMeta(
                    page = page,
                    size = size,
                    totalElements = totalElements,
                    totalPages = Math.ceil(totalElements.toDouble() / size).toInt()
                )
            )

        fun error(code: String, message: String?): Rest<Nothing> =
            Rest(
                success = false,
                error = ErrorDetail(code = code, message = message),
                meta = ApiMeta()
            )
    }
}

/** Common meta block included in every response. */
data class ApiMeta(
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)

/** Meta block for paginated list responses. */
data class PageMeta(
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

/** Error detail embedded inside a failed Rest response. */
data class ErrorDetail(
    val code: String,
    val message: String?,
    val details: Any? = null
)
