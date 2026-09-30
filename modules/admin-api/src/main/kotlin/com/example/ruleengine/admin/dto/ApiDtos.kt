package com.example.ruleengine.admin.dto

/**
 * 统一错误响应体（照搬旧 GlobalExceptionHandler 的 {code, message, error} 三字段结构）。
 */
data class ErrorResponse(
    val code: Int,
    val message: String,
    val error: String,
) {
    companion object {
        fun of(
            status: Int,
            message: String,
        ): ErrorResponse =
            ErrorResponse(
                code = status,
                message = message,
                error = statusText(status),
            )

        /** 与旧 GlobalExceptionHandler 中各分支的 error 文案保持一致 */
        fun statusText(status: Int): String =
            when (status) {
                400 -> "Bad Request"
                401 -> "Unauthorized"
                403 -> "Forbidden"
                404 -> "Not Found"
                405 -> "Method Not Allowed"
                415 -> "Unsupported Media Type"
                else -> "Internal Server Error"
            }
    }
}

/**
 * 分页响应体（旧 Spring Data Page 序列化中前端实际消费的五个字段；
 * 与旧 GrayscaleController 手工拼装的分页 Map 字段集完全一致）。
 */
data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
) {
    companion object {
        /**
         * 对内存中的全量列表做子列表分页（与旧 GrayscaleController 的分页算法一致）。
         */
        fun <T> of(
            all: List<T>,
            page: Int,
            size: Int,
        ): PageResponse<T> {
            val totalElements = all.size
            val totalPages = if (size > 0) Math.ceil(totalElements.toDouble() / size).toInt() else 1
            val fromIndex = minOf(page * size, totalElements)
            val toIndex = minOf(fromIndex + size, totalElements)
            return PageResponse(
                content = all.subList(fromIndex, toIndex),
                totalElements = totalElements.toLong(),
                totalPages = totalPages,
                number = page,
                size = size,
            )
        }
    }
}
