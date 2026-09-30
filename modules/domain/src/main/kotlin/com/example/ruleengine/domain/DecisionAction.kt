package com.example.ruleengine.domain

/**
 * 决策动作。
 *
 * 全集与产品核心语义一致（放行 / 人工审核 / 拒绝）：
 * - PASS：放行。旧 DecisionResponse.decision 的 "PASS"
 * - MANUAL_REVIEW：转人工审核。旧 v1.0 已规划该枚举值（见 PROJECT.md Known Tech Debt
 *   "MANUAL_REVIEW 仅为决策枚举值"），v1.1 人工审核闭环的直接输入
 * - REJECT：拒绝。旧 DecisionResponse.decision 的 "REJECT"，同时也是旧引擎在
 *   脚本异常、超时、返回值不可识别时的兜底动作（fail-safe 默认拒绝）
 */
enum class DecisionAction(
    val description: String,
) {
    PASS("放行"),
    MANUAL_REVIEW("人工审核"),
    REJECT("拒绝"),
    ;

    /** 对应旧 DecisionResponse.decision 字符串，便于 API 无损映射 */
    val codeName: String
        get() = name

    companion object {
        /** 解析旧引擎输出的 decision 字符串；无法识别时按 fail-safe 语义归为 REJECT */
        fun fromEngineOutput(raw: String?): DecisionAction =
            when (raw?.trim()?.uppercase()) {
                PASS.name -> PASS
                MANUAL_REVIEW.name -> MANUAL_REVIEW
                else -> REJECT
            }
    }
}
