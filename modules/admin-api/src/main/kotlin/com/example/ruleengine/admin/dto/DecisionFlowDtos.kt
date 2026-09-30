package com.example.ruleengine.admin.dto

import jakarta.validation.constraints.NotBlank

/**
 * 创建决策流请求（字段与校验消息与旧 CreateDecisionFlowRequest 逐字一致）。
 */
data class CreateDecisionFlowRequest(
    @field:NotBlank(message = "决策流Key不能为空")
    val flowKey: String = "",
    @field:NotBlank(message = "决策流名称不能为空")
    val flowName: String = "",
    val flowDescription: String? = null,
    @field:NotBlank(message = "流程图数据不能为空")
    val flowGraph: String = "",
)

/**
 * 更新决策流请求（对应旧 UpdateDecisionFlowRequest；全部字段可选，
 * flowGraph 为空表示仅更新元数据——与旧实现逐字段判空语义一致）。
 */
data class UpdateDecisionFlowRequest(
    val flowName: String? = null,
    val flowDescription: String? = null,
    val flowGraph: String? = null,
    val changeReason: String? = null,
)

/**
 * 决策流查询条件（对应旧 DecisionFlowQuery，多条件过滤；
 * 前端 DecisionFlowListPage 实际只传 status / keyword / enabled 三个字段，
 * 其余字段为旧契约完整性保留）。
 */
data class DecisionFlowQuery(
    val status: String? = null,
    val createdBy: String? = null,
    val enabled: Boolean? = null,
    val keyword: String? = null,
    val createdAtStart: java.time.LocalDateTime? = null,
    val createdAtEnd: java.time.LocalDateTime? = null,
    val updatedAtStart: java.time.LocalDateTime? = null,
    val updatedAtEnd: java.time.LocalDateTime? = null,
)

/**
 * 决策流响应体：字段集与旧 JPA 实体 DecisionFlow 的序列化形态逐字一致
 * （前端 frontend/src/types/decisionFlow.ts 的 DecisionFlow 接口是最终裁判）。
 *
 * 组装来源：storage 共享仓储 DecisionFlowMain（本批新增的决策流 CRUD 基于它，
 * 不在 admin 模块自建流表对象）；optLockVersion 为旧 JPA @Version 遗留列，恒 0。
 */
data class DecisionFlowResponse(
    val id: Long?,
    val flowKey: String,
    val flowName: String,
    val flowDescription: String?,
    val flowGraph: String,
    val version: Int,
    val status: String,
    val createdBy: String,
    val createdAt: java.time.Instant,
    val updatedBy: String?,
    val updatedAt: java.time.Instant?,
    val enabled: Boolean,
    val optLockVersion: Long,
    val activeVersion: Int?,
    val environmentId: Long?,
)

/**
 * 决策流版本响应体：字段集与旧 JPA 实体 DecisionFlowVersion 的序列化形态逐字一致
 * （消费方：frontend/src/api/grayscale.ts 的 VersionOption——灰度创建时选择决策流版本）。
 *
 * 与 2b 规则版本响应（id/ruleId=null）不同：storage 版本数据类携带真实代理键，
 * id / flowId 均以真实值回填（前端 importExport 与后续编辑器版本化消费 id 字段）。
 */
data class DecisionFlowVersionResponse(
    val id: Long?,
    val flowId: Long?,
    val flowKey: String,
    val version: Int,
    val flowGraph: String,
    val changeReason: String?,
    val changedBy: String,
    val changedAt: java.time.Instant,
    val isRollback: Boolean,
    val rollbackFromVersion: Int?,
    val status: String,
)

/**
 * 决策流版本回滚请求（对应旧 DecisionFlowVersionController 的
 * `POST /{flowKey}/versions/rollback`，请求体为 `{"targetVersion": N}`）。
 */
data class FlowRollbackRequest(
    val targetVersion: Int? = null,
)

/**
 * 决策流版本创建请求（新建草稿版本；旧契约未暴露该端点，
 * 路径/字段形状对齐 2b 规则版本的 CreateVersionRequest，供编辑器版本化演进使用）。
 */
data class CreateFlowVersionRequest(
    @field:NotBlank(message = "流程图数据不能为空")
    val flowGraph: String = "",
    val changeReason: String? = null,
)
