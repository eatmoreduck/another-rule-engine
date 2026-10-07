package com.eatmoreduck.ruleengine.decision

import com.eatmoreduck.ruleengine.decision.config.DecisionProperties
import com.eatmoreduck.ruleengine.decision.core.FeatureResolutionService
import com.eatmoreduck.ruleengine.engine.expression.AviatorExpressionService
import com.eatmoreduck.ruleengine.storage.feature.FeatureAlias
import com.eatmoreduck.ruleengine.storage.feature.FeatureDefinition
import com.eatmoreduck.ruleengine.storage.repository.FeatureCatalogRepository
import com.eatmoreduck.ruleengine.storage.repository.FeatureDefinitionQuery
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 衍生特征补算测试：决策特征解析阶段按 Aviator 公式惰性求值，失败按缺失降级。
 */
class FeatureResolutionDerivedTest {
    private val now = Instant.now()

    init {
        // 仓储为内存桩（不执行 SQL），仅满足 resolve 内 Exposed 事务上下文的连接要求
        Database.connect("jdbc:h2:mem:derived_test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    }

    private fun definition(
        code: String,
        sourceType: String = "DERIVED",
        expression: String? = null,
    ): FeatureDefinition =
        FeatureDefinition(
            code = code,
            name = code,
            dataType = "NUMBER",
            sourceType = sourceType,
            expression = expression,
            createdAt = now,
            updatedAt = now,
        )

    /** 最小仓储桩：仅衍生公式查询参与行为，其余方法不参与本批路径 */
    private class StubRepository(
        private val derived: Map<String, String>,
    ) : FeatureCatalogRepository {
        /** 衍生公式查询次数（缓存验证用） */
        var derivedQueries = 0

        override fun saveDefinition(definition: FeatureDefinition): FeatureDefinition = definition

        override fun findDefinitionByCode(code: String): FeatureDefinition? = null

        override fun existsDefinitionWithCode(code: String): Boolean = false

        override fun findDefinitionsByCodes(codes: Collection<String>): List<FeatureDefinition> = emptyList()

        override fun searchDefinitions(query: FeatureDefinitionQuery): List<FeatureDefinition> = emptyList()

        override fun findActiveWithExpression(): List<Pair<String, String>> {
            derivedQueries++
            return derived.entries.map { it.key to it.value }
        }

        override fun softDeleteDefinition(code: String): Boolean = false

        override fun saveAlias(alias: FeatureAlias): FeatureAlias = alias

        override fun findAliasByCode(aliasCode: String): FeatureAlias? = null

        override fun findAliasesByCanonicalCode(canonicalCode: String): List<FeatureAlias> = emptyList()

        override fun deleteAliasesByCanonicalCode(canonicalCode: String): Int = 0

        override fun resolveCode(code: String): FeatureDefinition? = null
    }

    private fun service(derived: Map<String, String>): FeatureResolutionService =
        FeatureResolutionService(StubRepository(derived), DecisionProperties(), AviatorExpressionService())

    @Test
    fun `请求缺失的衍生特征按公式补算`() {
        val resolved =
            runBlocking {
                service(mapOf("derived_score" to "amount * 2 + 100")).resolve(mapOf("amount" to 400), null, timeoutMs = 1_000)
            }
        assertEquals(900.0, (resolved["derived_score"] as Number).toDouble(), 0.0001)
    }

    @Test
    fun `请求已携带的值不被公式覆盖`() {
        val resolved =
            runBlocking {
                service(mapOf("derived_score" to "amount * 2 + 100"))
                    .resolve(mapOf("amount" to 400, "derived_score" to 1), null, timeoutMs = 1_000)
            }
        assertEquals(1, (resolved["derived_score"] as Number).toInt())
    }

    @Test
    fun `公式求值失败按缺失降级不抛异常`() {
        val resolved =
            runBlocking {
                service(mapOf("broken_feature" to "noSuchVariable * 2"))
                    .resolve(mapOf("amount" to 1), null, timeoutMs = 1_000)
            }
        assertFalse(resolved.containsKey("broken_feature"))
    }

    @Test
    fun `衍生公式缓存生效(目录查询仅首次发生)`() {
        val repository = StubRepository(mapOf("d" to "1 + 1"))
        val service = FeatureResolutionService(repository, DecisionProperties(), AviatorExpressionService())

        runBlocking {
            service.resolve(emptyMap(), null, timeoutMs = 1_000)
            service.resolve(emptyMap(), null, timeoutMs = 1_000)
            service.resolve(emptyMap(), null, timeoutMs = 1_000)
        }

        assertEquals(1, repository.derivedQueries)
    }
}
