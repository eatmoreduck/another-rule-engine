package com.eatmoreduck.ruleengine.admin.dto

/**
 * 环境响应体：字段集与旧 JPA 实体 Environment 的序列化形态逐字一致
 * （消费方：frontend/src/types/environment.ts 的 Environment 接口，环境页 EnvironmentPage）。
 */
data class EnvironmentResponse(
    val id: Long,
    val name: String,
    val type: String,
    val description: String?,
    val createdAt: java.time.Instant,
    val updatedAt: java.time.Instant?,
)

/**
 * 环境克隆请求（字段与默认值与旧 CloneEnvironmentRequest 一致：
 * overwrite 默认 false，operator 缺省时服务端兜底 "system"）。
 */
data class CloneEnvironmentRequest(
    val overwrite: Boolean? = null,
    val operator: String? = null,
)

/**
 * 环境克隆响应（对应旧 CloneEnvironmentResponse，含 success 工厂方法的固定消息文案）。
 */
data class CloneEnvironmentResponse(
    val success: Boolean,
    val clonedCount: Int,
    val skippedCount: Int,
    val message: String,
) {
    companion object {
        /** 与旧 CloneEnvironmentResponse.success 的消息文案逐字一致 */
        fun success(
            clonedCount: Int,
            skippedCount: Int,
        ): CloneEnvironmentResponse =
            CloneEnvironmentResponse(
                success = true,
                clonedCount = clonedCount,
                skippedCount = skippedCount,
                message = "克隆完成: 复制 $clonedCount 条规则, 跳过 $skippedCount 条",
            )
    }
}
