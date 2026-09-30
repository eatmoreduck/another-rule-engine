package com.example.ruleengine.decision.core

import com.example.ruleengine.decision.config.DecisionProperties
import com.example.ruleengine.storage.repository.FeatureCatalogRepository
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * 特征解析服务（三级策略，语义对齐旧 FeatureProviderService.getFeatures）：
 *
 * 1. 入参优先：请求携带的特征原样生效，并按目录别名关系做"规范编码 ↔ 别名"双写镜像；
 * 2. 缺失特征按 requiredFeatures 逐个解析规范编码后，先查本地特征值缓存，再并发调外部特征平台
 *    （协程 [withTimeoutOrNull] 超时降级为空 Map，绝不拖垮决策链路）；
 * 3. 仍缺失 → 内置默认值兜底（user_age=0 / user_level=NORMAL / order_amount=0.0 / risk_score=0.5），
 *    无默认值的特征补 NULL（脚本侧自行判空）。
 *
 * 与旧实现的结构差异（行为等价）：
 * - 别名解析与别名列表由无限 ConcurrentHashMap 改为带容量上限与 TTL 的 Caffeine（防泄漏）；
 * - 外部特征平台地址配置化（ruleengine.decision.feature-external-url），默认禁用——
 *   旧实现的外部地址在容器外不可达（异常吞掉返回空 Map），常态行为等价；
 * - 外部获取从 CompletableFuture 改为协程并发（批量一次调用 + 超时降级）。
 */
@Service
class FeatureResolutionService(
    private val featureCatalogRepository: FeatureCatalogRepository,
    properties: DecisionProperties,
) {
    private val log = LoggerFactory.getLogger(FeatureResolutionService::class.java)

    /** 特征编码 → 规范编码（别名兼容链路；目录变更经 TTL 后生效） */
    private val canonicalCodeCache =
        Caffeine
            .newBuilder()
            .maximumSize(properties.featureCodeCacheMaximumSize)
            .expireAfterWrite(Duration.ofSeconds(properties.featureCodeCacheExpireAfterWriteSeconds))
            .build<String, String>()

    /** 规范编码 → 别名列表（镜像双写用） */
    private val aliasListCache =
        Caffeine
            .newBuilder()
            .maximumSize(properties.featureCodeCacheMaximumSize)
            .expireAfterWrite(Duration.ofSeconds(properties.featureCodeCacheExpireAfterWriteSeconds))
            .build<String, List<String>>()

    /** 特征值缓存：规范编码 → 值（外部获取成功后回填；短 TTL 保新鲜） */
    private val featureValueCache =
        Caffeine
            .newBuilder()
            .maximumSize(properties.featureCacheMaximumSize)
            .expireAfterWrite(Duration.ofSeconds(properties.featureCacheExpireAfterWriteSeconds))
            .build<String, Any>()

    private val externalClient: RestClient? =
        properties.featureExternalUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { RestClient.builder().baseUrl(it).build() }

    /**
     * 解析决策可用的特征集合。
     *
     * @param inputFeatures 请求携带特征
     * @param requiredFeatures 规则声明的必需特征列表
     * @param timeoutMs 特征获取超时（毫秒）
     * @return 合并后的特征 Map（入参 + 镜像 + 补齐）
     */
    suspend fun resolve(
        inputFeatures: Map<String, Any?>?,
        requiredFeatures: List<String>?,
        timeoutMs: Long,
    ): Map<String, Any?> {
        val result = HashMap<String, Any?>(if (inputFeatures.isNullOrEmpty()) 16 else inputFeatures.size * 2)

        // 1. 入参镜像双写（规范编码 + 各别名可见）
        inputFeatures?.forEach { (key, value) ->
            putResolvedValue(result, key, value, populateCache = false)
        }

        // 2. 必需特征缺失分析（请求视角编码 → 规范编码）
        val requestedToCanonical = LinkedHashMap<String, String>()
        val canonicalMissing = LinkedHashSet<String>()
        requiredFeatures
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach { requested ->
                if (result.containsKey(requested)) return@forEach
                val canonical = resolveCanonicalCode(requested)
                requestedToCanonical[requested] = canonical
                if (!result.containsKey(canonical)) {
                    canonicalMissing.add(canonical)
                }
            }

        // 3. 本地特征值缓存补齐
        canonicalMissing.removeAll { canonical ->
            val cached = featureValueCache.getIfPresent(canonical)
            if (cached != null) {
                putResolvedValue(result, canonical, cached, populateCache = false)
                true
            } else {
                false
            }
        }

        // 4. 外部特征平台批量补齐（协程 + 超时降级）
        if (canonicalMissing.isNotEmpty()) {
            val external = fetchExternal(canonicalMissing.toList(), timeoutMs)
            external.forEach { (canonical, value) ->
                putResolvedValue(result, canonical, value, populateCache = true)
            }
            canonicalMissing.removeAll { result.containsKey(it) }
        }

        // 5. 默认值兜底，仍缺补 NULL
        canonicalMissing.forEach { canonical ->
            val default = DEFAULT_FEATURES[canonical]
            if (default != null) {
                putResolvedValue(result, canonical, default, populateCache = false)
            } else {
                result.putIfAbsent(canonical, null)
            }
        }

        // 6. 请求编码 ← 规范编码镜像回填
        requestedToCanonical.forEach { (requested, canonical) ->
            if (!result.containsKey(requested) && result.containsKey(canonical)) {
                result[requested] = result[canonical]
            }
        }
        return result
    }

    /** 单值落表：规范化键、规范键、全部别名键三路可见；外部来源回填特征值缓存 */
    private fun putResolvedValue(
        result: MutableMap<String, Any?>,
        featureCode: String,
        value: Any?,
        populateCache: Boolean,
    ) {
        val normalized = featureCode.trim()
        if (normalized.isEmpty()) return
        val canonical = resolveCanonicalCode(normalized)
        result[normalized] = value
        result[canonical] = value
        if (populateCache && value != null) {
            featureValueCache.put(canonical, value)
            featureValueCache.put(normalized, value)
        }
        listAliases(canonical).forEach { alias ->
            result.putIfAbsent(alias, value)
        }
    }

    /** 特征编码解析：目录定义直查 → 别名跳转规范编码；未命中原样返回（旧语义） */
    private fun resolveCanonicalCode(code: String): String =
        canonicalCodeCache.get(code) { key ->
            val definition =
                transaction {
                    featureCatalogRepository.resolveCode(key)
                }
            if (definition != null) {
                definition.code
            } else {
                key
            }
        } ?: code

    private fun listAliases(canonicalCode: String): List<String> =
        aliasListCache.get(canonicalCode) { key ->
            transaction {
                featureCatalogRepository
                    .findAliasesByCanonicalCode(key)
                    .map { it.aliasCode.trim() }
                    .filter { it.isNotEmpty() && it != key }
            }
        } ?: emptyList()

    /**
     * 整层失效全部特征缓存（阶段 5 失效广播入口）：特征目录定义与别名关系存在链式映射
     * （别名 → 规范编码 → 值镜像），无法按键精确圈定，统一整体失效；
     * 未收到广播时仍由各层 TTL 兜底。
     */
    fun invalidateAll() {
        canonicalCodeCache.invalidateAll()
        aliasListCache.invalidateAll()
        featureValueCache.invalidateAll()
    }

    /** 各层缓存条目数快照（可观测性 + 失效链路测试断言；先 cleanUp 强制结算挂起写入，读数确定） */
    fun cacheEntryCounts(): Map<String, Long> {
        canonicalCodeCache.cleanUp()
        aliasListCache.cleanUp()
        featureValueCache.cleanUp()
        return mapOf(
            "canonicalCode" to canonicalCodeCache.estimatedSize(),
            "aliasList" to aliasListCache.estimatedSize(),
            "featureValue" to featureValueCache.estimatedSize(),
        )
    }

    /** 外部特征平台批量获取：IO 协程执行，[timeoutMsMs] 内未完成或异常 → 空 Map（超时降级） */
    private suspend fun fetchExternal(
        codes: List<String>,
        timeoutMs: Long,
    ): Map<String, Any?> {
        val client = externalClient ?: return emptyMap()
        val fetched: Map<String, Any?>? =
            withTimeoutOrNull(timeoutMs) {
                try {
                    withContext(Dispatchers.IO) {
                        @Suppress("UNCHECKED_CAST")
                        val response =
                            client
                                .post()
                                .body(codes)
                                .retrieve()
                                .body(Map::class.java) as? Map<String, Any?>
                        response ?: emptyMap<String, Any?>()
                    }
                } catch (e: Exception) {
                    log.warn("外部特征平台获取失败: codes={}", codes, e)
                    emptyMap<String, Any?>()
                }
            }
        return fetched ?: emptyMap<String, Any?>().also { log.warn("外部特征平台获取超时({}ms): codes={}", timeoutMs, codes) }
    }

    companion object {
        /** 内置默认特征值（与旧 loadDefaultFeatures 逐字一致） */
        private val DEFAULT_FEATURES: Map<String, Any> =
            mapOf(
                "user_age" to 0,
                "user_level" to "NORMAL",
                "order_amount" to 0.0,
                "risk_score" to 0.5,
            )
    }
}
