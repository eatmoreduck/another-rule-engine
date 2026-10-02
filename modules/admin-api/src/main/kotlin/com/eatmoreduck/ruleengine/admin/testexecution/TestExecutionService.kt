package com.eatmoreduck.ruleengine.admin.testexecution

import com.eatmoreduck.ruleengine.admin.dto.TestResult
import com.eatmoreduck.ruleengine.engine.GroovyScriptEngine
import com.eatmoreduck.ruleengine.storage.repository.RuleRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleVersionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 规则测试执行服务：用 mock 特征数据在沙箱中执行规则脚本（语义照搬旧 TestExecutionService）。
 *
 * 与生产决策链路的区别（测试语义，不落入任何监控面）：
 * - 不写 execution_logs、不走灰度分流、不记 grayscale_metrics、不打决策耗时指标；
 * - 不做特征解析（测试参数即最终特征值，原样注入脚本）；
 * - 规则不存在 / 脚本异常 / 执行超时一律返回 200 + 失败形态的 [TestResult]
 *   （errorMessage 前缀"测试执行失败:"，与旧实现逐字一致）。
 *
 * 与旧实现的两处架构性差异：
 * - 脚本来源由主表 groovy_script 列改为当前版本的 definitionJson
 *   （与 [com.eatmoreduck.ruleengine.admin.rules.RuleService] 暴露 groovyScript 的口径一致）；
 * - 旧实现把测试参数逐键平铺为顶层绑定变量，新架构业务脚本契约为 features.<特征编码>
 *   （见 decision-api DecisionService.FEATURES_VARIABLE），此处以同名变量包裹注入。
 */
@Service
class TestExecutionService(
    private val ruleRepository: RuleRepository,
    private val versionRepository: RuleVersionRepository,
    @Qualifier("groovyScriptEngine")
    private val scriptEngine: GroovyScriptEngine,
) {
    /** 使用模拟数据测试规则（只读链路：不产生任何持久化副作用） */
    @Transactional(readOnly = true)
    fun executeTest(
        ruleKey: String,
        testData: Map<String, Any?>,
    ): TestResult {
        val startTime = System.currentTimeMillis()
        return try {
            ruleRepository.findByRuleKey(ruleKey)
                ?: return TestResult.failure(ruleKey, "规则不存在: $ruleKey", elapsedMs(startTime))
            // 脚本载荷取当前版本（版本号最大者）；无版本行时以空脚本走引擎校验失败路径
            val payload = versionRepository.findCurrentVersion(ruleKey)?.definitionJson.orEmpty()
            val result = scriptEngine.execute(payload, mapOf(FEATURES_VARIABLE to testData))
            parseTestResult(ruleKey, result, testData, elapsedMs(startTime))
        } catch (e: Exception) {
            log.error("测试规则执行失败: ruleKey={}", ruleKey, e)
            TestResult.failure(ruleKey, "测试执行失败: ${e.message}", elapsedMs(startTime))
        }
    }

    /**
     * 解析脚本执行结果为 [TestResult]（映射规则照搬旧 parseTestResult）：
     * - Boolean：true → PASS，false → REJECT，reason 固定"规则执行完成"；
     * - Map：取 decision / reason 键（缺失 decision 归 REJECT、缺失 reason 用默认文案）；
     * - String：原值即决策动作；
     * - 其他（含 null）：REJECT + "规则返回无效结果"。
     *
     * matchedConditions 为测试参数的快照（旧实现语义：逐键 "key = value" 回显，非真实命中判定）。
     */
    private fun parseTestResult(
        ruleKey: String,
        result: Any?,
        testData: Map<String, Any?>,
        executionTimeMs: Long,
    ): TestResult {
        val decision: String
        val reason: String
        when (result) {
            is Boolean -> {
                decision = if (result) "PASS" else "REJECT"
                reason = "规则执行完成"
            }

            is Map<*, *> -> {
                decision = result["decision"]?.toString() ?: "REJECT"
                reason = result["reason"]?.toString() ?: "规则执行完成"
            }

            is String -> {
                decision = result
                reason = "规则执行完成"
            }

            else -> {
                decision = "REJECT"
                reason = "规则返回无效结果"
            }
        }
        val matchedConditions = testData.entries.map { "${it.key} = ${it.value}" }
        return TestResult.success(ruleKey, decision, reason, executionTimeMs, matchedConditions, testData)
    }

    private fun elapsedMs(startTime: Long): Long = System.currentTimeMillis() - startTime

    companion object {
        private val log = LoggerFactory.getLogger(TestExecutionService::class.java)

        /** 特征注入脚本的绑定变量名（与 decision-api 的业务脚本契约一致：features.<特征编码>） */
        const val FEATURES_VARIABLE = "features"
    }
}
