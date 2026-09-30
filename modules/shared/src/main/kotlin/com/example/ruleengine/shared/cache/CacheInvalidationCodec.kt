package com.example.ruleengine.shared.cache

import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * 缓存失效事件的 JSON 编解码（Jackson 3，tools.jackson 命名空间）。
 *
 * 契约演进约定（向前兼容）：
 * - 新增 [CacheInvalidationType] 枚举值时，旧订阅方解码到未知 type 返回 null 并忽略，
 *   滚动发布期不因新枚举崩溃；
 * - 新增字段时，旧订阅方忽略未知字段（Jackson 默认行为）；
 * - 解码失败（载荷损坏）同样返回 null，由订阅方静默丢弃——失效广播本身幂等，
 *   丢弃一则事件的代价只是该键等待 TTL 兜底。
 */
object CacheInvalidationCodec {
    private val mapper: JsonMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .build()

    /** 编码为 JSON（契约字段封闭，编码不会失败） */
    fun encode(event: CacheInvalidationEvent): String = mapper.writeValueAsString(event)

    /**
     * 解码 JSON；载荷损坏或遇到未知 type 时返回 null（订阅方忽略）。
     */
    fun decodeOrNull(json: String): CacheInvalidationEvent? =
        try {
            mapper.readValue(json, CacheInvalidationEvent::class.java)
        } catch (e: IllegalArgumentException) {
            // 非法参数形态（如空串）与 Jackson 的部分绑定异常归口为"坏载荷"
            null
        } catch (e: JacksonException) {
            // Jackson 3 绑定异常基类：语法错误 / 未知枚举值 / 字段类型不符
            null
        }
}
