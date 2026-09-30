package com.example.ruleengine.shared.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 缓存失效事件契约单测：编解码往返 + 向前兼容（未知 type / 坏载荷 → null）。
 */
class CacheInvalidationCodecTest {
    @Test
    fun `encode then decode round-trips all types`() {
        for (type in CacheInvalidationType.entries) {
            val event = CacheInvalidationEvent(type, "key-$type", "admin-api", 1_727_600_000_000)
            val decoded = CacheInvalidationCodec.decodeOrNull(CacheInvalidationCodec.encode(event))
            assertEquals(event, decoded)
        }
    }

    @Test
    fun `null key round-trips as full-invalidation semantics`() {
        val event = CacheInvalidationEvent(CacheInvalidationType.GRAYSCALE, null, "admin-api", 42L)
        val decoded = CacheInvalidationCodec.decodeOrNull(CacheInvalidationCodec.encode(event))
        assertEquals(event, decoded)
        assertNull(decoded?.key)
    }

    @Test
    fun `decode tolerates unknown type for rolling upgrade`() {
        // 旧订阅方收到未来版本新增的枚举值：整体忽略，不崩溃
        val json = """{"type":"SOME_FUTURE_TYPE","key":"k","source":"admin-api","occurredAt":1}"""
        assertNull(CacheInvalidationCodec.decodeOrNull(json))
    }

    @Test
    fun `decode returns null on malformed payload`() {
        assertNull(CacheInvalidationCodec.decodeOrNull("not-json"))
        assertNull(CacheInvalidationCodec.decodeOrNull(""))
        assertNull(CacheInvalidationCodec.decodeOrNull("""{"key":"missing-type"}"""))
        assertNull(CacheInvalidationCodec.decodeOrNull("""{"type":"RULE","key":123}"""))
    }

    @Test
    fun `now factory stamps current time and source`() {
        val before = System.currentTimeMillis()
        val event = CacheInvalidationEvent.now(CacheInvalidationType.NAME_LIST, "GLOBAL", CacheInvalidationTopics.SOURCE_ADMIN_API)
        val after = System.currentTimeMillis()
        assertEquals(CacheInvalidationType.NAME_LIST, event.type)
        assertEquals("GLOBAL", event.key)
        assertEquals(CacheInvalidationTopics.SOURCE_ADMIN_API, event.source)
        assertEquals("admin-api", event.source)
        kotlin.test.assertTrue(event.occurredAt in before..after)
    }
}
