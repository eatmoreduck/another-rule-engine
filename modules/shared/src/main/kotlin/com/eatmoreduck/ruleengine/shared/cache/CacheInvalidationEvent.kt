package com.eatmoreduck.ruleengine.shared.cache

/**
 * 缓存失效广播事件（阶段 5 跨部署物契约，Redis pub/sub 载荷）。
 *
 * 链路：admin-api 配置变更提交事务后发布 → Redis 频道 [CacheInvalidationTopics.CACHE_INVALIDATE]
 * → decision-api 订阅方按 [type] 失效对应 Caffeine 缓存层。
 *
 * 本模块（shared/cache 包）是纯契约：零 Spring 依赖，仅 Jackson 3 做编解码。
 */
enum class CacheInvalidationType {
    /** 规则主行 / 规则版本载荷缓存（decision 侧 GrayscaleRouter 的 ruleMainCache / ruleVersionCache） */
    RULE,

    /** 决策流主行 / 流图载荷缓存（flowMainCache / flowGraphCache） */
    FLOW,

    /** 运行中灰度配置缓存（grayscaleCache，含负缓存条目） */
    GRAYSCALE,

    /** 特征目录解析缓存（canonicalCodeCache / aliasListCache / featureValueCache） */
    FEATURE,

    /** 黑白名单查询缓存（name list 存在性缓存，含负缓存条目） */
    NAME_LIST,
}

/**
 * 失效事件（不可变值对象）。
 *
 * @param type 失效的缓存类别
 * @param key 类别内的业务键（ruleKey / flowKey / 灰度目标 key / 特征编码 / listKey）；
 *   null 表示该类别整体失效。FEATURE 类别因别名关系链无法按键精确圈定，订阅方一律整层失效
 * @param source 发布方标识（如 admin-api），用于日志排查
 * @param occurredAt 事件产生时间（epoch millis），仅作排查线索，不用于丢弃旧事件
 *   （失效语义幂等，乱序/迟到事件最多导致一次多余重查）
 */
data class CacheInvalidationEvent(
    val type: CacheInvalidationType,
    val key: String?,
    val source: String?,
    val occurredAt: Long,
) {
    companion object {
        /** 构造以当前时间为戳的事件（发布侧统一入口，保证 occurredAt 语义单一） */
        fun now(
            type: CacheInvalidationType,
            key: String?,
            source: String,
        ): CacheInvalidationEvent = CacheInvalidationEvent(type, key, source, System.currentTimeMillis())
    }
}

/** Redis 频道与发布方标识常量 */
object CacheInvalidationTopics {
    /** 缓存失效广播频道（单频道 + 事件内 type 路由，避免多频道订阅管理） */
    const val CACHE_INVALIDATE: String = "ruleengine:cache:invalidate"

    /** 管理服务发布方标识 */
    const val SOURCE_ADMIN_API: String = "admin-api"
}
