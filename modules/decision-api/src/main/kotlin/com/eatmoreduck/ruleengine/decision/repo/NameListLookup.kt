package com.eatmoreduck.ruleengine.decision.repo

import com.eatmoreduck.ruleengine.decision.config.DecisionProperties
import com.github.benmanes.caffeine.cache.Caffeine
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant

/**
 * name_list 最小只读表对象（列与 V11/V12/V13 基线一致）：决策流黑白名单节点查询。
 * 名单管理（CRUD）属 admin-api 职责，决策侧只消费存在性判断。
 */
internal object NameListTable : Table("name_list") {
    val id = long("id")
    val listKey = varchar("list_key", 255)
    val listType = varchar("list_type", 10)
    val keyType = varchar("key_type", 20)
    val keyValue = varchar("key_value", 256)
    val expiredAt = timestamp("expired_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * 黑白名单存在性查询（对应旧 NameListService.existsInList → existsActiveEntry）。
 * 接口化以便流执行单测替换桩实现。
 */
interface NameListLookup {
    /** 指定名单键下 (listType, keyType, keyValue) 是否存在未过期条目 */
    fun existsActive(
        listKey: String,
        listType: String,
        keyType: String,
        keyValue: String,
    ): Boolean

    /**
     * 整层失效查询缓存（阶段 5 失效广播入口；名单变更面向 listKey 粒度，
     * 但缓存键为 (listKey, listType, keyType, keyValue) 四元组，无法按 listKey 精确圈定，
     * 统一整体失效）。默认空实现：单测桩实现无需感知失效链路。
     */
    fun invalidateAll() {}
}

/**
 * [NameListLookup] 的 Exposed 实现（语义：行存在且未过期——expired_at IS NULL
 * 或 > 当前时间——即命中；应用时钟取 NOW，与旧 JPQL 的 DB 时钟口径差异可忽略）。
 *
 * 热路径缓存：TTL 短（默认 5s），名单新增/删除的生效延迟上限即为 TTL；
 * 未命中结果同样缓存，防止恶意遍历打穿数据库。
 */
@Repository
class ExposedNameListLookup(
    properties: DecisionProperties,
) : NameListLookup {
    private val cache =
        Caffeine
            .newBuilder()
            .maximumSize(properties.nameListCacheMaximumSize)
            .expireAfterWrite(Duration.ofSeconds(properties.nameListExpireAfterWriteSeconds))
            .build<NameListKey, Boolean>()

    override fun existsActive(
        listKey: String,
        listType: String,
        keyType: String,
        keyValue: String,
    ): Boolean = cache.get(NameListKey(listKey, listType, keyType, keyValue)) { key -> lookup(key) } ?: false

    /** 名单变更广播到达时整层失效（含负缓存条目，防止"已删除名单仍命中"的旧值滞留） */
    override fun invalidateAll() {
        cache.invalidateAll()
    }

    /** 真实查询（事务上下文内）；名单键空白等价旧实现的空查询（恒不命中） */
    private fun lookup(key: NameListKey): Boolean =
        transaction {
            NameListTable
                .selectAll()
                .where {
                    (NameListTable.listKey eq key.listKey) and
                        (NameListTable.listType eq key.listType) and
                        (NameListTable.keyType eq key.keyType) and
                        (NameListTable.keyValue eq key.keyValue) and
                        (NameListTable.expiredAt.isNull() or (NameListTable.expiredAt greater Instant.now()))
                }.any()
        }

    private data class NameListKey(
        val listKey: String,
        val listType: String,
        val keyType: String,
        val keyValue: String,
    )
}
