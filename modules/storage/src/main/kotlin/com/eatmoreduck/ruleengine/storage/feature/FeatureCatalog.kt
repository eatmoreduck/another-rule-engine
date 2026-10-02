package com.eatmoreduck.ruleengine.storage.feature

import java.time.Instant

/**
 * 特征目录条目（feature_definition 表的行模型）。
 *
 * 注意：阶段 1 的 modules/domain 未建模特征目录（只有规则/版本/灰度三个聚合），
 * 本类型暂驻 storage 层，字段与 V1__init 的 feature_definition 列一一对应；
 * dataType / sourceType / status 沿用旧实现的宽松字符串（DDL 无 CHECK 约束），
 * 待领域模块收编后再收紧为枚举。
 */
data class FeatureDefinition(
    val id: Long? = null,
    /** 特征编码（业务键，唯一，比较忽略大小写） */
    val code: String,
    val name: String,
    /** 数据类型（旧值域：NUMBER / STRING / BOOLEAN ...） */
    val dataType: String,
    /** 来源类型（旧值域：INPUT / DERIVED ...） */
    val sourceType: String,
    val exampleValue: String? = null,
    val description: String? = null,
    /** 目录状态（旧默认 ACTIVE） */
    val status: String = "ACTIVE",
    val owner: String? = null,
    /** 创建时间：调用方显式传入（对应旧 Hibernate @CreationTimestamp 的应用侧生成职责） */
    val createdAt: Instant,
    /** 最近更新时间：调用方显式传入 */
    val updatedAt: Instant,
    /** 软删除标记（列表 includeDeleted 时可见，其余读取路径恒为未删除行） */
    val deleted: Boolean = false,
) {
    init {
        require(code.isNotBlank()) { "特征 code 不能为空白" }
        require(name.isNotBlank()) { "特征 name 不能为空白" }
    }
}

/**
 * 特征别名（feature_alias 表的行模型）：旧编码 → 规范编码（canonical_code）的兼容映射。
 */
data class FeatureAlias(
    val id: Long? = null,
    val aliasCode: String,
    val canonicalCode: String,
    /** 别名类别（旧默认 LEGACY） */
    val aliasType: String = "LEGACY",
    /** 别名状态（旧默认 ACTIVE） */
    val status: String = "ACTIVE",
    /** 创建时间：调用方显式传入 */
    val createdAt: Instant,
) {
    init {
        require(aliasCode.isNotBlank()) { "别名 aliasCode 不能为空白" }
        require(canonicalCode.isNotBlank()) { "别名 canonicalCode 不能为空白" }
    }
}
