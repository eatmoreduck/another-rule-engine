package com.eatmoreduck.ruleengine.admin.feature

import com.eatmoreduck.ruleengine.admin.cache.publishInvalidation
import com.eatmoreduck.ruleengine.admin.dto.FeatureDefinitionRequest
import com.eatmoreduck.ruleengine.admin.dto.FeatureDefinitionResponse
import com.eatmoreduck.ruleengine.admin.dto.FeatureExpressionTestRequest
import com.eatmoreduck.ruleengine.admin.dto.FeatureExpressionTestResponse
import com.eatmoreduck.ruleengine.admin.dto.FeatureValidationRequest
import com.eatmoreduck.ruleengine.admin.dto.FeatureValidationResponse
import com.eatmoreduck.ruleengine.admin.dto.PageResponse
import com.eatmoreduck.ruleengine.admin.dto.RuleReferenceResponse
import com.eatmoreduck.ruleengine.admin.grayscale.DecisionFlowSupportRepository
import com.eatmoreduck.ruleengine.dsl.ConditionNodeData
import com.eatmoreduck.ruleengine.dsl.DslParser
import com.eatmoreduck.ruleengine.dsl.ParseResult
import com.eatmoreduck.ruleengine.engine.expression.AviatorExpressionService
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationType
import com.eatmoreduck.ruleengine.storage.feature.FeatureAlias
import com.eatmoreduck.ruleengine.storage.feature.FeatureDefinition
import com.eatmoreduck.ruleengine.storage.repository.FeatureCatalogRepository
import com.eatmoreduck.ruleengine.storage.repository.FeatureDefinitionQuery
import com.eatmoreduck.ruleengine.storage.repository.RuleRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleSearchQuery
import com.eatmoreduck.ruleengine.storage.repository.RuleVersionRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Locale
import java.util.regex.Pattern

/**
 * 特征目录服务：定义 CRUD / 别名解析校验 / 引用查询。
 *
 * 业务规则照搬旧 FeatureCatalogService（校验消息与告警文案逐字一致）：
 * - 编码统一 trim 后入库，比较忽略大小写（仓储已按 lower() 实现）；
 * - 别名归属校验（别名不得等于规范编码 / 不得与已有编码冲突 / 不得被其他特征占用）；
 * - 别名全量替换式更新（先删后插）；
 * - 校验通道按「直接编码 → 别名跳转」解析，非 ACTIVE 定义保留告警（与旧实现一致，
 *   不使用仓储的 resolveCode——它会静默跳过非 ACTIVE 行，丢失"当前状态"告警）。
 */
@Service
class FeatureCatalogService(
    private val featureRepository: FeatureCatalogRepository,
    private val ruleRepository: RuleRepository,
    private val versionRepository: RuleVersionRepository,
    private val decisionFlowSupportRepository: DecisionFlowSupportRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val expressionEngine: AviatorExpressionService,
) {
    @Transactional(readOnly = true)
    fun searchDefinitions(
        page: Int,
        size: Int,
        keyword: String?,
        dataType: String?,
        sourceType: String?,
        status: String?,
        includeDeleted: Boolean = false,
    ): PageResponse<FeatureDefinitionResponse> {
        val definitions =
            featureRepository.searchDefinitions(
                FeatureDefinitionQuery(
                    keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
                    dataType = normalizeEnumFilter(dataType),
                    sourceType = normalizeEnumFilter(sourceType),
                    status = normalizeEnumFilter(status),
                    includeDeleted = includeDeleted,
                    limit = MAX_SCAN,
                ),
            )
        val aliasesByCode =
            featureRepository.findAliasesByCanonicalCodeFor(definitions.map { it.code })
        val responses =
            definitions.map { definition ->
                toResponse(definition, aliasesByCode[definition.code].orEmpty())
            }
        return PageResponse.of(responses, page, size)
    }

    @Transactional(readOnly = true)
    fun getDefinition(code: String): FeatureDefinitionResponse {
        val feature = getFeatureByCode(code)
        return toResponse(feature, listAliases(feature.code))
    }

    @Transactional
    fun createDefinition(request: FeatureDefinitionRequest): FeatureDefinitionResponse {
        val code = normalizeCode(request.code)
        if (featureRepository.existsDefinitionWithCode(code)) {
            throw IllegalArgumentException("特征编码已存在: $code")
        }
        validateAliasOwnership(code, request.aliases, selfCode = null)

        val now = Instant.now()
        val saved =
            featureRepository.saveDefinition(
                FeatureDefinition(
                    code = code,
                    name = request.name.trim(),
                    dataType = normalizeRequiredEnum(request.dataType, "特征类型不能为空"),
                    sourceType = normalizeRequiredEnum(request.sourceType, "特征来源不能为空"),
                    exampleValue = trimToNull(request.exampleValue),
                    expression = trimToNull(request.expression),
                    description = trimToNull(request.description),
                    status = normalizeOptionalEnum(request.status, "ACTIVE"),
                    owner = trimToNull(request.owner),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        replaceAliases(saved.code, request.aliases, now)
        // 特征目录/别名变更影响决策侧特征解析缓存（别名 → 规范编码 → 值镜像），广播失效
        eventPublisher.publishInvalidation(CacheInvalidationType.FEATURE, saved.code)
        return toResponse(saved, listAliases(saved.code))
    }

    /**
     * 软删除特征（别名行随删除一并清理），并广播特征缓存失效。
     *
     * 删除守卫（引用硬校验）：特征被规则/决策流引用时拒绝删除并返回引用清单——
     * 强引用下删除会导致规则执行时特征解析失败，必须先解绑再删。
     * 编码不存在或已删除时报"特征不存在"（与查询侧口径一致）。
     */
    @Transactional
    fun deleteDefinition(code: String) {
        val definition = getFeatureByCode(code)
        val references = getReferences(definition.code)
        if (references.isNotEmpty()) {
            val summary =
                references.joinToString("、") { ref ->
                    "${if (ref.type == "rule") "规则" else "决策流"}「${ref.name}」"
                }
            throw IllegalArgumentException("特征被引用，无法删除（共 ${references.size} 处）：$summary；请先解除引用")
        }
        featureRepository.softDeleteDefinition(definition.code)
        eventPublisher.publishInvalidation(CacheInvalidationType.FEATURE, definition.code)
        log.info("软删除特征: code={}", definition.code)
    }

    @Transactional
    fun updateDefinition(
        code: String,
        request: FeatureDefinitionRequest,
    ): FeatureDefinitionResponse {
        val existing = getFeatureByCode(code)
        val targetCode = normalizeCode(code)
        if (!targetCode.equals(normalizeCode(request.code), ignoreCase = true)) {
            throw IllegalArgumentException("特征编码不允许修改")
        }
        validateAliasOwnership(targetCode, request.aliases, selfCode = targetCode)

        val updatedAt = Instant.now()
        val saved =
            featureRepository.saveDefinition(
                existing.copy(
                    name = request.name.trim(),
                    dataType = normalizeRequiredEnum(request.dataType, "特征类型不能为空"),
                    sourceType = normalizeRequiredEnum(request.sourceType, "特征来源不能为空"),
                    exampleValue = trimToNull(request.exampleValue),
                    expression = trimToNull(request.expression),
                    description = trimToNull(request.description),
                    status = normalizeOptionalEnum(request.status, "ACTIVE"),
                    owner = trimToNull(request.owner),
                    updatedAt = updatedAt,
                ),
            )
        replaceAliases(saved.code, request.aliases, updatedAt)
        eventPublisher.publishInvalidation(CacheInvalidationType.FEATURE, saved.code)
        return toResponse(saved, listAliases(saved.code))
    }

    /**
     * 衍生特征公式试算：提取表达式变量名；提供采样值时执行求值。
     * 语法/求值错误以 ok=false + 可读 error 返回（不抛异常，供前端直接展示）。
     */
    fun testExpression(request: FeatureExpressionTestRequest): FeatureExpressionTestResponse {
        val variables =
            try {
                expressionEngine.variables(request.expression)
            } catch (e: Exception) {
                return FeatureExpressionTestResponse(ok = false, variables = emptyList(), error = e.message)
            }
        if (request.sampleValues.isEmpty()) {
            return FeatureExpressionTestResponse(ok = true, variables = variables)
        }
        return try {
            val result = expressionEngine.evaluate(request.expression, request.sampleValues)
            FeatureExpressionTestResponse(ok = true, variables = variables, result = result)
        } catch (e: Exception) {
            FeatureExpressionTestResponse(ok = false, variables = variables, error = e.message)
        }
    }

    /** 批量字段校验（告警文案与 valid 判定照搬旧实现） */
    @Transactional(readOnly = true)
    fun validate(request: FeatureValidationRequest): FeatureValidationResponse {
        val itemResults = mutableListOf<FeatureValidationResponse.ItemResult>()
        val warnings = mutableListOf<String>()
        val unknownFields = mutableListOf<String>()
        var valid = true

        for (item in request.items) {
            val resolution = resolve(item.fieldName)
            val itemWarnings = mutableListOf<String>()

            val definition = resolution.definition
            if (definition == null) {
                val unknown = normalizeCode(item.fieldName)
                unknownFields += unknown
                itemWarnings += "字段未收录于特征字典"
                warnings += String.format(Locale.ROOT, "字段 %s 未收录于特征字典", unknown)
                valid = false
                itemResults +=
                    FeatureValidationResponse.ItemResult(
                        fieldName = unknown,
                        found = false,
                        matchedByAlias = false,
                        warnings = itemWarnings,
                    )
                continue
            }

            val alias = resolution.alias
            if (alias != null) {
                itemWarnings +=
                    String.format(
                        Locale.ROOT,
                        "字段 %s 已映射为标准特征 %s",
                        normalizeCode(item.fieldName),
                        definition.code,
                    )
            }
            if (!definition.status.equals("ACTIVE", ignoreCase = true)) {
                itemWarnings +=
                    String.format(Locale.ROOT, "特征 %s 当前状态为 %s", definition.code, definition.status)
            }

            itemWarnings += checkOperatorCompatibility(definition.dataType, item.operator, item.threshold)
            if (itemWarnings.isNotEmpty()) {
                warnings += itemWarnings
            }
            if (itemWarnings.any { msg -> msg.contains("不匹配") || msg.contains("应为") || msg.contains("未收录") }) {
                valid = false
            }

            itemResults +=
                FeatureValidationResponse.ItemResult(
                    fieldName = normalizeCode(item.fieldName),
                    found = true,
                    matchedByAlias = alias != null,
                    canonicalCode = definition.code,
                    matchedAlias = alias?.aliasCode,
                    dataType = definition.dataType,
                    sourceType = definition.sourceType,
                    warnings = itemWarnings,
                )
        }

        return FeatureValidationResponse(
            valid = valid,
            warnings = warnings,
            unknownFields = unknownFields,
            items = itemResults,
        )
    }

    /** Groovy 脚本特征引用校验（规则保存时的告警日志来源；控制器直调，需自带事务边界） */
    @Transactional(readOnly = true)
    fun validateGroovyScript(groovyScript: String): FeatureValidationResponse =
        validate(
            FeatureValidationRequest(
                items = extractFieldsFromPayload(groovyScript).map { field -> FeatureValidationRequest.Item(fieldName = field) },
            ),
        )

    /** 特征被哪些规则/决策流引用（照搬旧 getReferences 的扫描口径与排序） */
    @Transactional(readOnly = true)
    fun getReferences(code: String): List<RuleReferenceResponse> {
        val definition = getFeatureByCode(code)
        val acceptedNames = linkedSetOf(definition.code)
        acceptedNames += listAliases(definition.code)

        val references = linkedMapOf<String, RuleReferenceResponse>()

        // 规则：非删除规则的当前版本载荷中 features.<field> 引用
        for (rule in ruleRepository.search(RuleSearchQuery(includeDeleted = false, limit = MAX_SCAN))) {
            val payload =
                versionRepository.findCurrentVersion(rule.ruleKey)?.definitionJson ?: ""
            val fields = extractFieldsFromPayload(payload)
            if (intersects(fields, acceptedNames)) {
                references["rule:${rule.ruleKey}"] =
                    RuleReferenceResponse(
                        type = "rule",
                        id = rule.id,
                        name = rule.ruleName,
                        key = rule.ruleKey,
                    )
            }
        }

        // 决策流：流程图条件节点的 fieldName 引用
        for (flow in decisionFlowSupportRepository.findAllFlowMains()) {
            val fields = extractFieldsFromFlowGraph(flow.flowGraph)
            if (intersects(fields, acceptedNames)) {
                references["decision_flow:${flow.flowKey}"] =
                    RuleReferenceResponse(
                        type = "decision_flow",
                        id = flow.id,
                        name = flow.flowName,
                        key = flow.flowKey,
                    )
            }
        }

        return references.values.sortedWith(compareBy({ it.type }, { it.key }))
    }

    // ---------- 私有辅助 ----------

    private fun getFeatureByCode(code: String): FeatureDefinition =
        featureRepository.findDefinitionByCode(normalizeCode(code))
            ?: throw IllegalArgumentException("特征不存在: $code")

    /** 校验通道的解析：直接编码 → 别名跳转（均不按状态过滤，保留"当前状态"告警） */
    private fun resolve(fieldName: String): Resolution {
        val normalized = normalizeCode(fieldName)
        val direct = featureRepository.findDefinitionByCode(normalized)
        if (direct != null) {
            return Resolution(direct, null)
        }
        val alias = featureRepository.findAliasByCode(normalized) ?: return Resolution(null, null)
        val definition = featureRepository.findDefinitionByCode(alias.canonicalCode)
        return Resolution(definition, alias)
    }

    private fun validateAliasOwnership(
        canonicalCode: String,
        aliases: Collection<String>,
        selfCode: String?,
    ) {
        for (alias in normalizeAliases(aliases)) {
            if (alias.equals(canonicalCode, ignoreCase = true)) {
                throw IllegalArgumentException("别名不能与特征编码相同: $alias")
            }
            featureRepository.findDefinitionByCode(alias)?.let { existing ->
                throw IllegalArgumentException("别名与已有特征编码冲突: ${existing.code}")
            }
            featureRepository.findAliasByCode(alias)?.let { existing ->
                if (selfCode == null || !existing.canonicalCode.equals(selfCode, ignoreCase = true)) {
                    throw IllegalArgumentException("别名已被其他特征占用: $alias")
                }
            }
        }
    }

    private fun replaceAliases(
        canonicalCode: String,
        aliases: Collection<String>,
        now: Instant,
    ) {
        featureRepository.deleteAliasesByCanonicalCode(canonicalCode)
        normalizeAliases(aliases).forEach { alias ->
            featureRepository.saveAlias(
                FeatureAlias(
                    aliasCode = alias,
                    canonicalCode = canonicalCode,
                    aliasType = "LEGACY",
                    status = "ACTIVE",
                    createdAt = now,
                ),
            )
        }
    }

    private fun listAliases(canonicalCode: String): List<String> =
        featureRepository.findAliasesByCanonicalCode(canonicalCode).map { it.aliasCode }

    /** 批量别名装配（按规范编码分组、组内排序，语义同旧 loadAliasesByCanonicalCode） */
    private fun FeatureCatalogRepository.findAliasesByCanonicalCodeFor(codes: Collection<String>): Map<String, List<String>> {
        if (codes.isEmpty()) return emptyMap()
        return codes
            .map { code -> code to findAliasesByCanonicalCode(code).map { it.aliasCode }.sorted() }
            .filter { it.second.isNotEmpty() }
            .toMap()
    }

    private fun toResponse(
        feature: FeatureDefinition,
        aliases: List<String>,
    ): FeatureDefinitionResponse =
        FeatureDefinitionResponse(
            id = feature.id,
            code = feature.code,
            name = feature.name,
            dataType = feature.dataType,
            sourceType = feature.sourceType,
            exampleValue = feature.exampleValue,
            expression = feature.expression,
            description = feature.description,
            status = feature.status,
            owner = feature.owner,
            createdAt = feature.createdAt,
            updatedAt = feature.updatedAt,
            deleted = feature.deleted,
            aliases = aliases.toList(),
        )

    /** 运算符兼容性告警（照搬旧 checkOperatorCompatibility） */
    private fun checkOperatorCompatibility(
        dataType: String?,
        operator: String?,
        threshold: Any?,
    ): List<String> {
        if (operator.isNullOrBlank() || dataType.isNullOrBlank()) {
            return emptyList()
        }
        val normalizedType = dataType.trim().uppercase(Locale.ROOT)
        val normalizedOperator = operator.trim().uppercase(Locale.ROOT)
        val warnings = mutableListOf<String>()

        if (normalizedOperator in setOf("GT", "GE", "LT", "LE")) {
            if (normalizedType !in NUMERIC_TYPES) {
                warnings += String.format(Locale.ROOT, "特征 %s 与运算符 %s 不匹配，应为数值类型", normalizedType, normalizedOperator)
            }
            if (threshold != null && !isNumeric(threshold)) {
                warnings += "阈值类型不匹配，应为数值"
            }
        }

        if (normalizedOperator in setOf("CONTAINS", "NOT_CONTAINS", "IN", "NOT_IN") &&
            normalizedType !in TEXT_TYPES
        ) {
            warnings += String.format(Locale.ROOT, "特征 %s 与运算符 %s 不匹配，应为字符串或集合类型", normalizedType, normalizedOperator)
        }

        if (normalizedType in BOOLEAN_TYPES && threshold != null && !isBoolean(threshold)) {
            warnings += "阈值类型不匹配，应为布尔值"
        }

        return warnings
    }

    /** 从规则载荷提取 features.<field> 引用（旧正则 features\.(\w+)） */
    private fun extractFieldsFromPayload(payload: String?): Set<String> {
        val result = linkedSetOf<String>()
        if (payload.isNullOrBlank()) {
            return result
        }
        val matcher = FEATURE_PATTERN.matcher(payload)
        while (matcher.find()) {
            result += matcher.group(1)
        }
        return result
    }

    private fun extractFieldsFromFlowGraph(flowGraph: String): Set<String> {
        val result = linkedSetOf<String>()
        if (flowGraph.isBlank()) {
            return result
        }
        when (val parsed = DslParser.parseFlowGraph(flowGraph)) {
            is ParseResult.Success -> {
                parsed.value.nodes.forEach { node ->
                    val fieldName = (node.data as? ConditionNodeData)?.fieldName
                    if (!fieldName.isNullOrBlank()) {
                        result += fieldName
                    }
                }
            }

            is ParseResult.Failure -> {
                log.warn("解析决策流特征引用失败: {}", parsed.error.reason)
            }
        }
        return result
    }

    private fun intersects(
        left: Set<String>,
        right: Set<String>,
    ): Boolean = left.any { item -> right.any { it.equals(item, ignoreCase = true) } }

    private fun normalizeAliases(aliases: Collection<String>): LinkedHashSet<String> =
        aliases
            .mapNotNull(::trimToNull)
            .map(::normalizeCode)
            .toCollection(LinkedHashSet())

    private fun normalizeCode(raw: String): String {
        val value = trimToNull(raw) ?: throw IllegalArgumentException("特征编码不能为空")
        return value
    }

    private fun normalizeEnumFilter(raw: String?): String? = trimToNull(raw)?.uppercase(Locale.ROOT)

    private fun normalizeRequiredEnum(
        raw: String?,
        message: String,
    ): String = trimToNull(raw)?.uppercase(Locale.ROOT) ?: throw IllegalArgumentException(message)

    private fun normalizeOptionalEnum(
        raw: String?,
        defaultValue: String,
    ): String = trimToNull(raw)?.uppercase(Locale.ROOT) ?: defaultValue

    private fun trimToNull(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        return trimmed.ifEmpty { null }
    }

    private fun isNumeric(value: Any): Boolean =
        when (value) {
            is Number -> true
            is String -> value.matches(Regex("-?\\d+(\\.\\d+)?"))
            else -> false
        }

    private fun isBoolean(value: Any): Boolean =
        when (value) {
            is Boolean -> true
            is String -> value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)
            else -> false
        }

    private data class Resolution(
        val definition: FeatureDefinition?,
        val alias: FeatureAlias?,
    )

    companion object {
        private val log = LoggerFactory.getLogger(FeatureCatalogService::class.java)

        private val FEATURE_PATTERN: Pattern = Pattern.compile("features\\.(\\w+)")
        private val NUMERIC_TYPES = setOf("NUMBER", "INTEGER", "LONG", "DOUBLE", "DECIMAL")
        private val TEXT_TYPES = setOf("STRING", "TEXT", "LIST", "ARRAY")
        private val BOOLEAN_TYPES = setOf("BOOLEAN")

        /** 内存分页的最大扫描行数（与 RuleService 同一防护口径） */
        const val MAX_SCAN: Int = 10_000
    }
}
