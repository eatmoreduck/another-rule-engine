package com.eatmoreduck.ruleengine.admin.environment

import com.eatmoreduck.ruleengine.admin.data.EnvironmentsTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 环境行模型（列与 V7 迁移一致） */
data class EnvironmentRow(
    val id: Long,
    val name: String,
    val type: String,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant?,
)

/** 环境下规则概览（克隆与查询用的最小列集，列与 rules 表 V1/V10/V21 一致） */
data class EnvironmentRuleRow(
    val id: Long,
    val ruleKey: String,
    val ruleName: String,
    val ruleDescription: String?,
    val groovyScript: String,
    val version: Int,
    val enabled: Boolean,
    val deleted: Boolean,
    val createdBy: String,
    val createdAt: Instant,
    val updatedBy: String?,
    val updatedAt: Instant?,
    val environmentId: Long?,
)

/**
 * 环境数据访问（environments 表 CRUD，V7 种子含 DEV/STAGING/PRODUCTION 三行）。
 */
interface EnvironmentRepository {
    fun findAll(): List<EnvironmentRow>

    fun findById(id: Long): EnvironmentRow?

    fun findByName(name: String): EnvironmentRow?

    fun existsByName(name: String): Boolean
}

/**
 * 环境克隆的规则表读写支撑。
 *
 * 说明：克隆需按 environmentId 读 rules 主表并向目标环境写入行，storage 模块的
 * RuleRepository 不含按环境查询的口径（且本批禁止跨模块改动），故照 2b
 * DecisionFlowSupportRepository 先例在 admin 模块内自持最小表对象直读/写既有表。
 *
 * 与旧实现的边界：旧 JPA 时代克隆直接插入"同 ruleKey 新行"，但 V1 起 rules.rule_key
 * 即为全局 UNIQUE，跨环境同键插入必然违反约束（旧实现此处爆 DataIntegrityViolation
 * → 500）；本接口提供 existsRuleKeyAnyRow 供服务层在写入前探测，将冲突行
 * 收敛为跳过（见 EnvironmentService 与汇报差异点）。
 */
interface EnvironmentRuleSupportRepository {
    /** 环境下的全部规则（含禁用与软删，照旧 findByEnvironmentId 不过滤） */
    fun findByEnvironmentId(environmentId: Long): List<EnvironmentRuleRow>

    /** 目标环境已有的 ruleKey 集合（旧 findRuleKeysByEnvironmentId） */
    fun findRuleKeysByEnvironmentId(environmentId: Long): Set<String>

    /**
     * ruleKey 是否已被 rules 表任何行占用（全局唯一约束探测，克隆写入前置检查）。
     * 注意必须包含源行自身：源行就持有该键，跨环境复制（同键插入新行）在全局唯一
     * 约束下必然违规（旧实现此处直接 500），探测结果驱动"计为跳过"。
     */
    fun existsRuleKeyAnyRow(ruleKey: String): Boolean

    /** 向目标环境插入规则副本行（冗余脚本列写入源值；克隆路径专用） */
    fun insertEnvironmentCopy(
        source: EnvironmentRuleRow,
        targetEnvironmentId: Long,
        operator: String,
    ): Long

    /** 覆盖模式：更新目标环境同名规则的内容列（照旧 overwrite 分支的更新列集） */
    fun updateRuleContent(
        ruleId: Long,
        source: EnvironmentRuleRow,
        operator: String,
    )
}

/** [EnvironmentRepository] 的 Exposed 实现 */
@Repository
@Transactional(readOnly = true)
class ExposedEnvironmentRepository : EnvironmentRepository {
    private val table = EnvironmentsTable

    override fun findAll(): List<EnvironmentRow> = table.selectAll().orderBy(table.id to SortOrder.ASC).map(::toRow)

    override fun findById(id: Long): EnvironmentRow? =
        table
            .selectAll()
            .where { table.id eq id }
            .singleOrNull()
            ?.let(::toRow)

    override fun findByName(name: String): EnvironmentRow? =
        table
            .selectAll()
            .where { table.name eq name }
            .singleOrNull()
            ?.let(::toRow)

    override fun existsByName(name: String): Boolean = table.selectAll().where { table.name eq name }.any()

    private fun toRow(row: ResultRow): EnvironmentRow =
        EnvironmentRow(
            id = row[table.id],
            name = row[table.name],
            type = row[table.type],
            description = row[table.description],
            createdAt = row[table.createdAt],
            updatedAt = row[table.updatedAt],
        )
}

/** [EnvironmentRuleSupportRepository] 的 Exposed 实现：直读/写 rules 主表最小列集 */
@Repository
@Transactional(readOnly = true)
class ExposedEnvironmentRuleSupportRepository : EnvironmentRuleSupportRepository {
    // rules 表最小表对象（列定义与 storage 的 RulesTable 一致；本模块自持，避免跨模块耦合）
    private object RulesTable : org.jetbrains.exposed.v1.core.Table("rules") {
        val id = long("id").autoIncrement()
        val ruleKey = varchar("rule_key", 255)
        val ruleName = varchar("rule_name", 255)
        val ruleDescription = text("rule_description").nullable()
        val groovyScript = text("groovy_script")
        val version = integer("version")
        val enabled = bool("enabled")
        val deleted = bool("deleted")
        val createdBy = varchar("created_by", 255)
        val createdAt = timestamp("created_at")
        val updatedBy = varchar("updated_by", 255).nullable()
        val updatedAt = timestamp("updated_at").nullable()
        val environmentId = long("environment_id").nullable()
    }

    override fun findByEnvironmentId(environmentId: Long): List<EnvironmentRuleRow> =
        RulesTable
            .selectAll()
            .where { RulesTable.environmentId eq environmentId }
            .orderBy(RulesTable.id to SortOrder.ASC)
            .map(::toRow)

    override fun findRuleKeysByEnvironmentId(environmentId: Long): Set<String> =
        RulesTable
            .selectAll()
            .where { RulesTable.environmentId eq environmentId }
            .map { it[RulesTable.ruleKey] }
            .toSet()

    override fun existsRuleKeyAnyRow(ruleKey: String): Boolean = RulesTable.selectAll().where { RulesTable.ruleKey eq ruleKey }.any()

    @Transactional
    override fun insertEnvironmentCopy(
        source: EnvironmentRuleRow,
        targetEnvironmentId: Long,
        operator: String,
    ): Long =
        RulesTable.insert { statement ->
            statement[RulesTable.ruleKey] = source.ruleKey
            statement[RulesTable.ruleName] = source.ruleName
            statement[RulesTable.ruleDescription] = source.ruleDescription
            statement[RulesTable.groovyScript] = source.groovyScript
            statement[RulesTable.version] = source.version
            statement[RulesTable.enabled] = source.enabled
            statement[RulesTable.deleted] = source.deleted
            statement[RulesTable.createdBy] = operator
            statement[RulesTable.createdAt] = Instant.now()
            statement[RulesTable.updatedBy] = operator
            statement[RulesTable.updatedAt] = Instant.now()
            statement[RulesTable.environmentId] = targetEnvironmentId
        } get RulesTable.id

    @Transactional
    override fun updateRuleContent(
        ruleId: Long,
        source: EnvironmentRuleRow,
        operator: String,
    ) {
        RulesTable.update({ RulesTable.id eq ruleId }) { statement ->
            statement[RulesTable.groovyScript] = source.groovyScript
            statement[RulesTable.ruleName] = source.ruleName
            statement[RulesTable.ruleDescription] = source.ruleDescription
            statement[RulesTable.version] = source.version
            statement[RulesTable.updatedBy] = operator
            statement[RulesTable.updatedAt] = Instant.now()
        }
    }

    private fun toRow(row: ResultRow): EnvironmentRuleRow =
        EnvironmentRuleRow(
            id = row[RulesTable.id],
            ruleKey = row[RulesTable.ruleKey],
            ruleName = row[RulesTable.ruleName],
            ruleDescription = row[RulesTable.ruleDescription],
            groovyScript = row[RulesTable.groovyScript],
            version = row[RulesTable.version],
            enabled = row[RulesTable.enabled],
            deleted = row[RulesTable.deleted],
            createdBy = row[RulesTable.createdBy],
            createdAt = row[RulesTable.createdAt],
            updatedBy = row[RulesTable.updatedBy],
            updatedAt = row[RulesTable.updatedAt],
            environmentId = row[RulesTable.environmentId],
        )
}
