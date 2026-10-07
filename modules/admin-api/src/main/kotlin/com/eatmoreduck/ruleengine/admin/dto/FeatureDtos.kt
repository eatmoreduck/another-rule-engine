package com.eatmoreduck.ruleengine.admin.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

/**
 * 特征定义创建/更新请求（对应旧 FeatureDefinitionRequest，已移除旧版 sensitivity 敏感级别字段）。
 */
data class FeatureDefinitionRequest(
    @field:NotBlank(message = "特征编码不能为空")
    val code: String = "",
    @field:NotBlank(message = "特征名称不能为空")
    val name: String = "",
    @field:NotBlank(message = "特征类型不能为空")
    val dataType: String = "",
    @field:NotBlank(message = "特征来源不能为空")
    val sourceType: String = "",
    val exampleValue: String? = null,
    /** 衍生特征公式（Aviator 表达式，sourceType=DERIVED 时使用） */
    val expression: String? = null,
    val description: String? = null,
    val status: String = "ACTIVE",
    val owner: String? = null,
    val aliases: List<String> = emptyList(),
)

/**
 * 特征定义响应体（对应旧 FeatureDefinitionResponse，前端 frontend/src/types/featureCatalog.ts）。
 */
data class FeatureDefinitionResponse(
    val id: Long?,
    val code: String,
    val name: String,
    val dataType: String,
    val sourceType: String,
    val exampleValue: String?,
    val expression: String?,
    val description: String?,
    val status: String,
    val owner: String?,
    val createdAt: java.time.Instant?,
    val updatedAt: java.time.Instant?,
    val deleted: Boolean = false,
    val aliases: List<String>,
)

/**
 * 特征校验请求（对应旧 FeatureValidationRequest + 嵌套 Item）。
 */
data class FeatureValidationRequest(
    @field:Valid
    @field:NotEmpty(message = "校验项不能为空")
    val items: List<Item> = emptyList(),
) {
    data class Item(
        @field:NotBlank(message = "字段名不能为空")
        val fieldName: String = "",
        val operator: String? = null,
        val threshold: Any? = null,
    )
}

/**
 * 特征校验响应（对应旧 FeatureValidationResponse + 嵌套 ItemResult）。
 */
data class FeatureValidationResponse(
    val valid: Boolean,
    val warnings: List<String> = emptyList(),
    val unknownFields: List<String> = emptyList(),
    val items: List<ItemResult> = emptyList(),
) {
    data class ItemResult(
        val fieldName: String,
        val found: Boolean,
        val matchedByAlias: Boolean,
        val canonicalCode: String? = null,
        val matchedAlias: String? = null,
        val dataType: String? = null,
        val sourceType: String? = null,
        val warnings: List<String> = emptyList(),
    )
}
