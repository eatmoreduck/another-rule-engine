package com.example.ruleengine.admin

import com.example.ruleengine.admin.audit.AuditLogRepository
import com.example.ruleengine.admin.audit.AuditLogRow
import com.example.ruleengine.admin.environment.EnvironmentRepository
import com.example.ruleengine.admin.environment.EnvironmentRow
import com.example.ruleengine.admin.environment.EnvironmentRuleRow
import com.example.ruleengine.admin.environment.EnvironmentRuleSupportRepository
import com.example.ruleengine.admin.namelist.NameListEntryRow
import com.example.ruleengine.admin.namelist.NameListRepository
import com.example.ruleengine.storage.repository.DecisionFlowMain
import com.example.ruleengine.storage.repository.DecisionFlowRepository
import com.example.ruleengine.storage.repository.DecisionFlowVersion
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/** 内存版决策流仓储（服务层单元测试用，行为对齐 ExposedDecisionFlowRepository） */
class FakeDecisionFlowRepository : DecisionFlowRepository {
    val mains = LinkedHashMap<String, DecisionFlowMain>()
    val versions = LinkedHashMap<String, MutableList<DecisionFlowVersion>>()
    private val mainIdSeq = AtomicLong(0)
    private val versionIdSeq = AtomicLong(0)

    override fun findMain(flowKey: String): DecisionFlowMain? = mains[flowKey]

    override fun findAllMains(): List<DecisionFlowMain> = mains.values.sortedBy { it.id }

    override fun findVersion(
        flowKey: String,
        version: Int,
    ): DecisionFlowVersion? = versions[flowKey]?.firstOrNull { it.version == version }

    override fun findVersionsByFlowKey(flowKey: String): List<DecisionFlowVersion> =
        (versions[flowKey] ?: emptyList()).sortedByDescending { it.version }

    override fun insertMain(main: DecisionFlowMain): DecisionFlowMain {
        val withId = main.copy(id = mainIdSeq.incrementAndGet())
        mains[withId.flowKey] = withId
        versions[withId.flowKey] = mutableListOf()
        return withId
    }

    override fun insertVersion(version: DecisionFlowVersion): DecisionFlowVersion {
        val withId = version.copy(id = versionIdSeq.incrementAndGet())
        versions.getOrPut(withId.flowKey) { mutableListOf() }.add(withId)
        return withId
    }

    override fun updateMainMeta(
        flowKey: String,
        flowName: String,
        flowDescription: String?,
        environmentId: Long?,
        updatedBy: String,
    ): Int {
        val main = mains[flowKey] ?: return 0
        mains[flowKey] =
            main.copy(
                flowName = flowName,
                flowDescription = flowDescription,
                environmentId = environmentId,
                updatedBy = updatedBy,
                updatedAt = Instant.now(),
            )
        return 1
    }

    override fun updateMainPointer(
        flowKey: String,
        version: Int,
        activeVersion: Int?,
        flowGraph: String,
        updatedBy: String,
    ): Int {
        val main = mains[flowKey] ?: return 0
        mains[flowKey] =
            main.copy(
                version = version,
                activeVersion = activeVersion,
                flowGraph = flowGraph,
                updatedBy = updatedBy,
                updatedAt = Instant.now(),
            )
        return 1
    }

    override fun updateMainStatus(
        flowKey: String,
        status: String,
        updatedBy: String,
    ): Int = mutateMain(flowKey) { it.copy(status = status, updatedBy = updatedBy, updatedAt = Instant.now()) }

    override fun setMainEnabled(
        flowKey: String,
        enabled: Boolean,
        updatedBy: String,
    ): Int = mutateMain(flowKey) { it.copy(enabled = enabled, updatedBy = updatedBy, updatedAt = Instant.now()) }

    override fun updateVersionStatus(
        flowKey: String,
        version: Int,
        status: String,
    ): Int {
        val list = versions[flowKey] ?: return 0
        val index = list.indexOfFirst { it.version == version }
        if (index < 0) return 0
        list[index] = list[index].copy(status = status)
        return 1
    }

    private fun mutateMain(
        flowKey: String,
        mutate: (DecisionFlowMain) -> DecisionFlowMain,
    ): Int {
        val main = mains[flowKey] ?: return 0
        mains[flowKey] = mutate(main)
        return 1
    }
}

/** 内存版名单仓储 */
class FakeNameListRepository : NameListRepository {
    val entries = LinkedHashMap<Long, NameListEntryRow>()
    private val idSeq = AtomicLong(0)

    override fun insert(entry: NameListEntryRow): NameListEntryRow {
        val withId = entry.copy(id = idSeq.incrementAndGet())
        entries[withId.id] = withId
        return withId
    }

    override fun findById(id: Long): NameListEntryRow? = entries[id]

    override fun existsByBusinessKey(
        listKey: String,
        listType: String,
        keyType: String,
        keyValue: String,
    ): Boolean = entries.values.any { it.listKey == listKey && it.listType == listType && it.keyType == keyType && it.keyValue == keyValue }

    override fun search(
        listKey: String?,
        listType: String?,
        keyType: String?,
    ): List<NameListEntryRow> =
        entries.values
            .asSequence()
            .filter { listKey == null || it.listKey == listKey }
            .filter { listType == null || it.listType == listType }
            .filter { keyType == null || it.keyType == keyType }
            .sortedBy { it.id }
            .toList()

    override fun findDistinctListKeys(): List<String> =
        entries.values
            .map { it.listKey }
            .distinct()
            .sorted()

    override fun deleteById(id: Long): Int = if (entries.remove(id) != null) 1 else 0
}

/** 内存版审计日志仓储（只读查询语义对齐 ExposedAuditLogRepository） */
class FakeAuditLogRepository : AuditLogRepository {
    val rows = mutableListOf<AuditLogRow>()
    private val idSeq = AtomicLong(0)

    fun seed(row: AuditLogRow) {
        rows += row.copy(id = idSeq.incrementAndGet())
    }

    override fun findByEntity(
        entityType: String,
        entityId: String,
    ): List<AuditLogRow> =
        rows
            .filter { it.entityType == entityType && it.entityId == entityId }
            .sortedByDescending { it.operationTime }

    override fun findByOperatorAndTimeRange(
        operator: String,
        start: Instant,
        end: Instant,
    ): List<AuditLogRow> =
        rows
            .filter { it.operator == operator && !it.operationTime.isBefore(start) && !it.operationTime.isAfter(end) }
            .sortedByDescending { it.operationTime }

    override fun findByConditions(
        operator: String?,
        entityType: String?,
        operation: String?,
        startTime: Instant?,
        endTime: Instant?,
    ): List<AuditLogRow> =
        rows
            .asSequence()
            .filter { row -> operator?.let { row.operator.contains(it) } ?: true }
            .filter { entityType == null || it.entityType == entityType }
            .filter { operation == null || it.operation == operation }
            .filter { startTime == null || !it.operationTime.isBefore(startTime) }
            .filter { endTime == null || !it.operationTime.isAfter(endTime) }
            .sortedByDescending { it.operationTime }
            .toList()
}

/** 内存版环境仓储 */
class FakeEnvironmentRepository : EnvironmentRepository {
    val environments = LinkedHashMap<Long, EnvironmentRow>()
    private val idSeq = AtomicLong(0)

    fun seed(
        name: String,
        type: String,
    ): EnvironmentRow {
        val row =
            EnvironmentRow(
                id = idSeq.incrementAndGet(),
                name = name,
                type = type,
                description = null,
                createdAt = Instant.now(),
                updatedAt = null,
            )
        environments[row.id] = row
        return row
    }

    override fun findAll(): List<EnvironmentRow> = environments.values.sortedBy { it.id }

    override fun findById(id: Long): EnvironmentRow? = environments[id]

    override fun findByName(name: String): EnvironmentRow? = environments.values.firstOrNull { it.name == name }

    override fun existsByName(name: String): Boolean = environments.values.any { it.name == name }
}

/** 内存版环境规则支撑仓储（读写 rules 主表行） */
class FakeEnvironmentRuleSupportRepository : EnvironmentRuleSupportRepository {
    val rules = LinkedHashMap<Long, EnvironmentRuleRow>()
    private val idSeq = AtomicLong(0)

    fun seed(rule: EnvironmentRuleRow): EnvironmentRuleRow {
        val withId = if (rule.id == 0L) rule.copy(id = idSeq.incrementAndGet()) else rule
        rules[withId.id] = withId
        return withId
    }

    override fun findByEnvironmentId(environmentId: Long): List<EnvironmentRuleRow> =
        rules.values.filter { it.environmentId == environmentId }.sortedBy { it.id }

    override fun findRuleKeysByEnvironmentId(environmentId: Long): Set<String> =
        findByEnvironmentId(environmentId).map { it.ruleKey }.toSet()

    override fun existsRuleKeyAnyRow(ruleKey: String): Boolean = rules.values.any { it.ruleKey == ruleKey }

    override fun insertEnvironmentCopy(
        source: EnvironmentRuleRow,
        targetEnvironmentId: Long,
        operator: String,
    ): Long {
        val copy =
            source.copy(
                id = idSeq.incrementAndGet(),
                createdBy = operator,
                createdAt = Instant.now(),
                updatedBy = operator,
                updatedAt = Instant.now(),
                environmentId = targetEnvironmentId,
            )
        rules[copy.id] = copy
        return copy.id
    }

    override fun updateRuleContent(
        ruleId: Long,
        source: EnvironmentRuleRow,
        operator: String,
    ) {
        val existing = rules[ruleId] ?: return
        rules[ruleId] =
            existing.copy(
                groovyScript = source.groovyScript,
                ruleName = source.ruleName,
                ruleDescription = source.ruleDescription,
                version = source.version,
                updatedBy = operator,
                updatedAt = Instant.now(),
            )
    }
}
