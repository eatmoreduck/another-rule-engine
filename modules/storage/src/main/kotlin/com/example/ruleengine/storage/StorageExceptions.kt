package com.example.ruleengine.storage

/**
 * 仓储层引用的关联实体不存在（如 rule_versions.rule_id 按 ruleKey 解析不到规则）。
 */
class EntityNotFoundException(
    entityType: String,
    key: Any,
) : RuntimeException("实体不存在: $entityType key=$key")

/**
 * 库中数据违反领域不变式或格式损坏，无法映射为领域模型。
 *
 * 与旧行为（坏数据静默降级为"不命中/忽略"）不同，新仓储选择 fail fast：
 * 静默降级会让"灰度配置全量失效"这类事故不可见。
 */
class StorageDataCorruptionException(
    detail: String,
    cause: Throwable? = null,
) : RuntimeException("存储数据损坏: $detail", cause)

/**
 * 更新目标行不存在或已被并发修改（按 id 更新影响 0 行时抛出）。
 *
 * 注意与 java.util.ConcurrentModificationException 无关，仅是仓储层语义。
 */
class StorageConflictException(
    entityType: String,
    key: Any,
) : RuntimeException("更新目标不存在或已被并发修改: $entityType key=$key")
