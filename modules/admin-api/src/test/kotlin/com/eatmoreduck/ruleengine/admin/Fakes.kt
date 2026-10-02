package com.eatmoreduck.ruleengine.admin

import com.eatmoreduck.ruleengine.admin.auth.AuthRepository
import com.eatmoreduck.ruleengine.admin.auth.AuthUser
import com.eatmoreduck.ruleengine.admin.grayscale.DecisionFlowMain
import com.eatmoreduck.ruleengine.admin.grayscale.DecisionFlowSupportRepository
import com.eatmoreduck.ruleengine.admin.grayscale.DecisionFlowVersionRow
import com.eatmoreduck.ruleengine.admin.grayscale.GrayscaleMetricRow
import com.eatmoreduck.ruleengine.admin.grayscale.GrayscaleMetricsRepository
import com.eatmoreduck.ruleengine.domain.GrayscaleRelease
import com.eatmoreduck.ruleengine.domain.GrayscaleStatus
import com.eatmoreduck.ruleengine.domain.GrayscaleTarget
import com.eatmoreduck.ruleengine.domain.Rule
import com.eatmoreduck.ruleengine.domain.RuleStatus
import com.eatmoreduck.ruleengine.domain.RuleVersion
import com.eatmoreduck.ruleengine.domain.VersionStatus
import com.eatmoreduck.ruleengine.storage.feature.FeatureAlias
import com.eatmoreduck.ruleengine.storage.feature.FeatureDefinition
import com.eatmoreduck.ruleengine.storage.repository.FeatureCatalogRepository
import com.eatmoreduck.ruleengine.storage.repository.FeatureDefinitionQuery
import com.eatmoreduck.ruleengine.storage.repository.GrayscaleReleaseRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleSearchQuery
import com.eatmoreduck.ruleengine.storage.repository.RuleVersionRepository
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/** 内存版规则仓储（服务层单元测试用，行为对齐 Exposed 实现的查询语义） */
class FakeRuleRepository : RuleRepository {
    val rules = LinkedHashMap<String, Rule>()
    private val idSeq = AtomicLong(0)

    override fun purgeDeleted(ruleKey: String) {
        rules.remove(ruleKey)
    }

    override fun save(rule: Rule): Rule {
        val withId = if (rule.id == null) rule.copy(id = idSeq.incrementAndGet()) else rule
        rules[withId.ruleKey] = withId
        return withId
    }

    override fun findByRuleKey(ruleKey: String): Rule? = rules[ruleKey]

    override fun existsByRuleKey(ruleKey: String): Boolean = rules.containsKey(ruleKey)

    override fun findEnabled(environmentId: Long?): List<Rule> =
        rules.values
            .filter { it.status == RuleStatus.ENABLED }
            .filter { environmentId == null || it.environmentId == environmentId }
            .sortedBy { it.id }

    override fun search(query: RuleSearchQuery): List<Rule> =
        rules.values
            .asSequence()
            .filter { query.includeDeleted || it.status != RuleStatus.DELETED }
            .filter { rule ->
                query.keyword?.let { keyword ->
                    rule.ruleKey.contains(keyword, ignoreCase = true) ||
                        rule.ruleName.contains(keyword, ignoreCase = true)
                } ?: true
            }.filter { query.teamId == null || it.teamId == query.teamId }
            .filter { query.environmentId == null || it.environmentId == query.environmentId }
            .sortedBy { it.id }
            .drop(query.offset.toInt())
            .take(query.limit)
            .toList()
}

/** 内存版规则版本仓储（以 (ruleKey, version) 为业务键） */
class FakeRuleVersionRepository : RuleVersionRepository {
    val versions = LinkedHashMap<Pair<String, Int>, RuleVersion>()

    override fun save(version: RuleVersion): RuleVersion {
        versions[version.ruleKey to version.version] = version
        return version
    }

    override fun findByRuleKeyAndVersion(
        ruleKey: String,
        version: Int,
    ): RuleVersion? = versions[ruleKey to version]

    override fun existsByRuleKeyAndVersion(
        ruleKey: String,
        version: Int,
    ): Boolean = versions.containsKey(ruleKey to version)

    override fun findByRuleKey(ruleKey: String): List<RuleVersion> =
        versions
            .filterKeys { it.first == ruleKey }
            .values
            .sortedByDescending { it.version }

    override fun findCurrentVersion(ruleKey: String): RuleVersion? = findByRuleKey(ruleKey).firstOrNull()

    override fun findActiveVersion(ruleKey: String): RuleVersion? = findByRuleKey(ruleKey).firstOrNull { it.status == VersionStatus.ACTIVE }

    override fun findByStatus(
        ruleKey: String,
        status: VersionStatus,
    ): List<RuleVersion> = findByRuleKey(ruleKey).filter { it.status == status }
}

/** 内存版灰度发布仓储 */
class FakeGrayscaleReleaseRepository : GrayscaleReleaseRepository {
    val releases = LinkedHashMap<Long, GrayscaleRelease>()
    private val idSeq = AtomicLong(0)

    override fun save(release: GrayscaleRelease): GrayscaleRelease {
        val withId = if (release.id == null) release.copy(id = idSeq.incrementAndGet()) else release
        releases[withId.id!!] = withId
        return withId
    }

    override fun findById(id: Long): GrayscaleRelease? = releases[id]

    override fun findRunningByTarget(target: GrayscaleTarget): GrayscaleRelease? =
        releases.values
            .filter { it.target == target && it.status == GrayscaleStatus.RUNNING }
            .maxByOrNull { it.createdAt }

    override fun findByTarget(target: GrayscaleTarget): List<GrayscaleRelease> =
        releases.values
            .filter { it.target == target }
            .sortedByDescending { it.createdAt }

    override fun findByStatus(status: GrayscaleStatus): List<GrayscaleRelease> =
        releases.values
            .filter { it.status == status }
            .sortedByDescending { it.createdAt }

    override fun findAll(): List<GrayscaleRelease> = releases.values.sortedByDescending { it.createdAt }
}

/** 内存版特征目录仓储（编码与别名比较忽略大小写） */
class FakeFeatureCatalogRepository : FeatureCatalogRepository {
    val definitions = LinkedHashMap<String, FeatureDefinition>()
    val aliases = LinkedHashMap<String, FeatureAlias>()

    override fun saveDefinition(definition: FeatureDefinition): FeatureDefinition {
        definitions[definition.code.lowercase()] = definition
        return definition
    }

    override fun findDefinitionByCode(code: String): FeatureDefinition? = definitions[code.lowercase()]

    override fun existsDefinitionWithCode(code: String): Boolean = definitions.containsKey(code.lowercase())

    override fun findDefinitionsByCodes(codes: Collection<String>): List<FeatureDefinition> = codes.mapNotNull(::findDefinitionByCode)

    override fun searchDefinitions(query: FeatureDefinitionQuery): List<FeatureDefinition> =
        definitions.values
            .asSequence()
            .filter { definition ->
                query.keyword?.let { keyword ->
                    definition.code.contains(keyword, ignoreCase = true) ||
                        definition.name.contains(keyword, ignoreCase = true)
                } ?: true
            }.filter { query.dataType == null || it.dataType.equals(query.dataType, ignoreCase = true) }
            .filter { query.sourceType == null || it.sourceType.equals(query.sourceType, ignoreCase = true) }
            .filter { query.sensitivity == null || it.sensitivity.equals(query.sensitivity, ignoreCase = true) }
            .filter { query.status == null || it.status.equals(query.status, ignoreCase = true) }
            .sortedBy { it.code }
            .drop(query.offset.toInt())
            .take(query.limit)
            .toList()

    override fun saveAlias(alias: FeatureAlias): FeatureAlias {
        aliases[alias.aliasCode.lowercase()] = alias
        return alias
    }

    override fun findAliasByCode(aliasCode: String): FeatureAlias? = aliases[aliasCode.lowercase()]

    override fun findAliasesByCanonicalCode(canonicalCode: String): List<FeatureAlias> =
        aliases.values
            .filter { it.canonicalCode.equals(canonicalCode, ignoreCase = true) }
            .sortedBy { it.aliasCode }

    override fun deleteAliasesByCanonicalCode(canonicalCode: String): Int {
        val matching =
            aliases.entries
                .filter { it.value.canonicalCode.equals(canonicalCode, ignoreCase = true) }
                .map { it.key }
        matching.forEach { aliases.remove(it) }
        return matching.size
    }

    override fun resolveCode(code: String): FeatureDefinition? {
        findDefinitionByCode(code)?.let { if (it.status == "ACTIVE") return it }
        val alias = findAliasByCode(code) ?: return null
        if (alias.status != "ACTIVE") return null
        val definition = findDefinitionByCode(alias.canonicalCode) ?: return null
        return if (definition.status == "ACTIVE") definition else null
    }
}

/** 内存版认证仓储 */
class FakeAuthRepository : AuthRepository {
    val usersByPassword = LinkedHashMap<String, AuthUser>()
    var lastLoginUpdate: Pair<Long, Instant>? = null

    fun seed(user: AuthUser) {
        usersByPassword[user.username] = user
    }

    override fun findByUsername(username: String): AuthUser? = usersByPassword[username]

    override fun findById(userId: Long): AuthUser? = usersByPassword.values.firstOrNull { it.id == userId }

    override fun updateLastLoginAt(
        userId: Long,
        at: Instant,
    ) {
        lastLoginUpdate = userId to at
    }

    override fun findRoleCodes(userId: Long): List<String> = if (userId == 1L) listOf("SUPER_ADMIN") else emptyList()

    override fun findPermissionCodes(userId: Long): List<String> = emptyList()
}

/** 内存版决策流支撑 */
class FakeDecisionFlowSupportRepository : DecisionFlowSupportRepository {
    val flows = LinkedHashMap<String, DecisionFlowMain>()
    val flowVersions = LinkedHashMap<Pair<String, Int>, DecisionFlowVersionRow>()

    override fun findFlowMain(flowKey: String): DecisionFlowMain? = flows[flowKey]

    override fun findAllFlowMains(): List<DecisionFlowMain> = flows.values.toList()

    override fun findFlowVersion(
        flowKey: String,
        version: Int,
    ): DecisionFlowVersionRow? = flowVersions[flowKey to version]

    override fun switchFlowVersion(
        flowKey: String,
        grayscaleVersion: Int,
        flowGraph: String,
    ) {
        flows[flowKey]?.let { main ->
            flows[flowKey] =
                main.copy(version = grayscaleVersion, flowGraph = flowGraph, activeVersion = grayscaleVersion)
        }
    }

    override fun markFlowVersionCanary(
        flowKey: String,
        version: Int,
    ) {
        flowVersions[flowKey to version]?.let { row ->
            flowVersions[flowKey to version] = row.copy(status = "CANARY")
        }
    }
}

/** 内存版灰度指标仓储 */
class FakeGrayscaleMetricsRepository : GrayscaleMetricsRepository {
    val metrics = LinkedHashMap<Pair<Long, Int>, GrayscaleMetricRow>()

    override fun initMetrics(
        configId: Long,
        currentVersion: Int,
        grayscaleVersion: Int,
    ) {
        listOf(currentVersion, grayscaleVersion).forEach { version ->
            metrics.putIfAbsent(
                configId to version,
                GrayscaleMetricRow(version, 0, 0, 0, 0),
            )
        }
    }

    override fun findByConfigId(configId: Long): List<GrayscaleMetricRow> =
        metrics.entries.filter { it.key.first == configId }.map { it.value }
}

/**
 * 收集发布事件的 [ApplicationEventPublisher] 桩：缓存失效广播（阶段 5）链路的单测观测点。
 * 单测无需 Redis——Spring 事件在服务内同步发布，转发 Redis 属于上下文层的 Forwarder 职责。
 */
class RecordingEventPublisher : ApplicationEventPublisher {
    val events = mutableListOf<Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> published(): List<T> = events.toList() as List<T>

    override fun publishEvent(event: Any) {
        events.add(event)
    }
}
