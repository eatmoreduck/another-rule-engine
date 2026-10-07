package com.eatmoreduck.ruleengine.storage.repository

import com.eatmoreduck.ruleengine.storage.feature.FeatureAlias
import com.eatmoreduck.ruleengine.storage.feature.FeatureDefinition

/**
 * 特征目录仓储（对应旧 FeatureDefinitionRepository / FeatureAliasRepository 的派生查询职责）。
 *
 * 编码（code / aliasCode）的比较语义沿用旧派生查询：忽略大小写。
 */
interface FeatureCatalogRepository {
    /**
     * 新增或更新特征定义。
     * - [FeatureDefinition.id] 为 null → 插入（code 重复将触发数据库唯一约束异常）
     * - 非 null → 按 id 全列更新，目标行不存在时抛 [com.eatmoreduck.ruleengine.storage.StorageConflictException]
     */
    fun saveDefinition(definition: FeatureDefinition): FeatureDefinition

    /** 按编码精确查询（忽略大小写）；已软删除的行不返回 */
    fun findDefinitionByCode(code: String): FeatureDefinition?

    /** 编码是否已存在（忽略大小写）；仅统计未软删除的行，同名可重建 */
    fun existsDefinitionWithCode(code: String): Boolean

    /** 批量按编码加载（保持 [codes] 原顺序，缺失的编码不出现） */
    fun findDefinitionsByCodes(codes: Collection<String>): List<FeatureDefinition>

    /** 组合条件分页查询（管理端目录检索）；条件全部可选；已软删除的行不返回 */
    fun searchDefinitions(query: FeatureDefinitionQuery): List<FeatureDefinition>

    /** 活跃的衍生特征公式列表（code → expression，sourceType=DERIVED 且 expression 非空） */
    fun findActiveWithExpression(): List<Pair<String, String>>

    /**
     * 软删除特征（deleted = TRUE，仅作用于未删除行），并物理清理其全部别名行
     * （兼容映射随特征失效，也释放别名占用供后续特征使用）。
     *
     * @return 是否删到行（编码不存在或已删除返回 false）
     */
    fun softDeleteDefinition(code: String): Boolean

    /** 新增别名（aliasCode 重复将触发数据库唯一约束异常） */
    fun saveAlias(alias: FeatureAlias): FeatureAlias

    /** 按别名编码查询（忽略大小写） */
    fun findAliasByCode(aliasCode: String): FeatureAlias?

    /** 某规范编码下的全部别名，按别名编码升序 */
    fun findAliasesByCanonicalCode(canonicalCode: String): List<FeatureAlias>

    /** 删除某规范编码的全部别名，返回删除行数（对应旧 deleteByCanonicalCode） */
    fun deleteAliasesByCanonicalCode(canonicalCode: String): Int

    /**
     * 编码解析（别名兼容链路）：先按编码直接命中，未命中再经别名表跳转到规范定义。
     * 任一环节状态为非 ACTIVE 的行不参与解析（与旧 FeatureCatalogService 语义一致）。
     */
    fun resolveCode(code: String): FeatureDefinition?
}

/** 特征定义列表查询条件（对应旧 FeatureDefinitionRepository.search 的组合过滤） */
data class FeatureDefinitionQuery(
    /** 关键字：命中 code 或 name（大小写不敏感的包含匹配） */
    val keyword: String? = null,
    val dataType: String? = null,
    val sourceType: String? = null,
    val status: String? = null,
    /** 是否包含已软删除行（管理端"显示已删除"开关） */
    val includeDeleted: Boolean = false,
    val limit: Int = 100,
    val offset: Long = 0,
)
