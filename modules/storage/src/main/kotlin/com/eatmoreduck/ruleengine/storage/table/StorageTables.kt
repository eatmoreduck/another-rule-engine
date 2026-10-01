package com.eatmoreduck.ruleengine.storage.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * Exposed 表对象：映射旧后端 Flyway 基线（src/main/resources/db/migration V1..V25）已存在的表。
 *
 * 约定：
 * - 本模块不做 SchemaUtils 驱动的 DDL 演进——真实 schema 事实标准是 Flyway 迁移脚本，
 *   表对象只负责"类型安全地读写既有列"；
 * - 可空性与迁移脚本 DDL 对齐（DEFAULT 无 NOT NULL 的列一律 .nullable()，由映射层兜底/校验）；
 * - 时间戳列为 PG `TIMESTAMP`（无时区），经 exposed-java-time 映射为 [java.time.Instant]，
 *   往返解释依赖 JVM 默认时区，与旧 Hibernate `LocalDateTime` 行为一致。
 */
object RulesTable : Table("rules") {
    val id = long("id").autoIncrement()
    val ruleKey = varchar("rule_key", 255).uniqueIndex()
    val ruleName = varchar("rule_name", 255)
    val ruleDescription = text("rule_description").nullable()

    /**
     * 冗余主表脚本列：领域模型已把定义载荷收敛到 rule_versions（[RuleVersionsTable.groovyScript]），
     * 本列新写入一律占位空串，仅满足 NOT NULL 约束，读路径忽略。
     */
    val groovyScript = text("groovy_script")
    val version = integer("version")
    val activeVersion = integer("active_version").nullable()
    val enabled = bool("enabled")
    val deleted = bool("deleted")
    val createdBy = varchar("created_by", 255)
    val createdAt = timestamp("created_at")
    val updatedBy = varchar("updated_by", 255).nullable()
    val updatedAt = timestamp("updated_at").nullable()

    /** 旧 JPA @Version 乐观锁遗留列（DEFAULT 0，可空）：领域模型已剔除，新写入固定 0 */
    val optLockVersion = long("opt_lock_version").nullable()
    val environmentId = long("environment_id").nullable()
    val teamId = long("team_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

object RuleVersionsTable : Table("rule_versions") {
    val id = long("id").autoIncrement()
    val ruleId = long("rule_id")
    val ruleKey = varchar("rule_key", 255)
    val version = integer("version")

    /** 规则定义载荷：领域模型为 definitionJson 原样字符串，历史列名保留（语义已迁移） */
    val groovyScript = text("groovy_script")
    val changeReason = text("change_reason").nullable()
    val changedBy = varchar("changed_by", 255)
    val changedAt = timestamp("changed_at")

    /** DDL 无 NOT NULL（仅 DEFAULT FALSE）：可空以防御历史行 NULL，映射层兜底为 false */
    val isRollback = bool("is_rollback").nullable()
    val rollbackFromVersion = integer("rollback_from_version").nullable()

    /** DDL 无 NOT NULL（V14 DEFAULT 'ACTIVE'）：可空以防御历史行 NULL，映射层 fail fast */
    val status = varchar("status", 20).nullable()

    override val primaryKey = PrimaryKey(id)
}

object GrayscaleConfigsTable : Table("grayscale_configs") {
    val id = long("id").autoIncrement()

    /**
     * 灰度目标业务键的兼容列：V14 引入 target_type/target_key 之前，灰度只作用于规则；
     * 写路径统一存 target.key（决策流灰度同值），保持旧索引 idx_grayscale_configs_rule_key 有效。
     */
    val ruleKey = varchar("rule_key", 255)
    val currentVersion = integer("current_version")
    val grayscaleVersion = integer("grayscale_version")
    val grayscalePercentage = integer("grayscale_percentage")
    val status = varchar("status", 20)
    val startedAt = timestamp("started_at").nullable()
    val completedAt = timestamp("completed_at").nullable()

    /** DDL 无 NOT NULL（V14 DEFAULT 'RULE'）：映射层兜底为 RULE */
    val targetType = varchar("target_type", 20).nullable()
    val targetKey = varchar("target_key", 255).nullable()

    /** DDL 无 NOT NULL（V18 DEFAULT 'PERCENTAGE'）：映射层兜底为 PERCENTAGE */
    val strategyType = varchar("strategy_type", 30).nullable()

    /** 特征匹配条件 JSON（`[{"field":..,"operator":..,"value":..}]`），仅 FEATURE 策略使用 */
    val featureRules = text("feature_rules").nullable()

    /** 白名单用户 ID（逗号分隔），仅 WHITELIST 策略使用 */
    val whitelistIds = text("whitelist_ids").nullable()
    val dualRunEnabled = bool("dual_run_enabled").nullable()
    val description = text("description").nullable()
    val createdBy = varchar("created_by", 255).nullable()
    val createdAt = timestamp("created_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object FeatureDefinitionsTable : Table("feature_definition") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 120).uniqueIndex()
    val name = varchar("name", 200)
    val dataType = varchar("data_type", 50)
    val sourceType = varchar("source_type", 50)
    val exampleValue = text("example_value").nullable()
    val description = text("description").nullable()
    val sensitivity = varchar("sensitivity", 50)
    val status = varchar("status", 20)
    val owner = varchar("owner", 100).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object FeatureAliasesTable : Table("feature_alias") {
    val id = long("id").autoIncrement()
    val aliasCode = varchar("alias_code", 120).uniqueIndex()
    val canonicalCode = varchar("canonical_code", 120)
    val aliasType = varchar("alias_type", 50)
    val status = varchar("status", 20)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
