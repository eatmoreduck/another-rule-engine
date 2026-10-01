package com.eatmoreduck.ruleengine.admin.grayscale

import com.eatmoreduck.ruleengine.admin.dto.GrayscaleConfigResponse
import com.eatmoreduck.ruleengine.domain.FeatureCondition
import com.eatmoreduck.ruleengine.domain.GrayscalePolicy
import com.eatmoreduck.ruleengine.domain.GrayscaleRelease
import com.eatmoreduck.ruleengine.domain.GrayscaleStatus
import org.springframework.stereotype.Component

/**
 * 灰度聚合 → API DTO 的组装器。
 *
 * 旧平铺列的回显语义（与旧 GrayscaleConfigResponse.fromEntity 对齐）：
 * - strategyType / featureRules / whitelistIds 从领域策略反向展开（Feature 策略重新序列化为
 *   旧 JSON 形状 `[{"field":..,"operator":..,"value":..}]`，Whitelist 以逗号串回显）；
 * - grayscalePercentage 的"进度"语义由状态呈现：COMPLETED 固定回显 100（全量）、
 *   ROLLED_BACK 固定回显 0，其余回显策略参数（与旧服务把实体列改写为 100/0 后返回的观感一致，
 *   领域模型与存储不再冗余记录该进度列）。
 */
@Component
class GrayscaleAssembler {
    fun toResponse(release: GrayscaleRelease): GrayscaleConfigResponse =
        GrayscaleConfigResponse(
            id = release.id,
            ruleKey = release.target.key,
            targetType = release.target.type.name,
            targetKey = release.target.key,
            currentVersion = release.currentVersion,
            grayscaleVersion = release.grayscaleVersion,
            grayscalePercentage = displayPercentage(release),
            status = release.status.name,
            statusDescription = statusDescription(release.status),
            strategyType = release.policy.typeName,
            featureRules = featureRulesText(release.policy),
            whitelistIds = whitelistText(release.policy),
            dualRunEnabled = release.dualRunEnabled,
            description = release.description,
            startedAt = release.startedAt,
            completedAt = release.completedAt,
            createdBy = release.createdBy,
            createdAt = release.createdAt,
        )

    private fun displayPercentage(release: GrayscaleRelease): Int =
        when (release.status) {
            GrayscaleStatus.COMPLETED -> {
                100
            }

            GrayscaleStatus.ROLLED_BACK -> {
                0
            }

            else -> {
                when (val policy = release.policy) {
                    is GrayscalePolicy.Percentage -> policy.percentage
                    else -> 0
                }
            }
        }

    private fun statusDescription(status: GrayscaleStatus): String =
        when (status) {
            GrayscaleStatus.DRAFT -> "草稿"
            GrayscaleStatus.RUNNING -> "运行中"
            GrayscaleStatus.PAUSED -> "已暂停"
            GrayscaleStatus.COMPLETED -> "已完成"
            GrayscaleStatus.ROLLED_BACK -> "已回滚"
        }

    private fun featureRulesText(policy: GrayscalePolicy): String? =
        when (policy) {
            is GrayscalePolicy.Feature -> {
                policy.conditions.joinToString(
                    separator = ",",
                    prefix = "[",
                    postfix = "]",
                ) { condition -> conditionJson(condition) }
            }

            else -> {
                null
            }
        }

    private fun whitelistText(policy: GrayscalePolicy): String? =
        when (policy) {
            is GrayscalePolicy.Whitelist -> policy.userIds.sorted().joinToString(separator = ",")
            else -> null
        }

    /** 单条条件的旧 JSON 形状（字段顺序与 GrayscalePolicyCodec 的编码一致；value 需 JSON 字符串转义） */
    private fun conditionJson(condition: FeatureCondition): String {
        val escapedValue =
            condition.value
                ?.replace("\\", "\\\\")
                ?.replace("\"", "\\\"")
        return """{"field":"${condition.field}","operator":"${condition.operator.name}","value":""" +
            (if (escapedValue != null) "\"$escapedValue\"" else "null") +
            "}"
    }
}
