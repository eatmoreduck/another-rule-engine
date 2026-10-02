package com.eatmoreduck.ruleengine.decision.config

import com.eatmoreduck.ruleengine.engine.GroovyScriptEngine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * 决策侧沙箱脚本引擎装配。
 *
 * 与 admin-api 的校验链引擎（bean 名 groovyScriptEngine，默认 5s 执行超时）是两个
 * 共存的 bean：合并部署物内按名区分，决策热路径必须配紧超时——
 * 引擎默认执行超时由 [DecisionProperties.executionTimeoutMs] 注入（默认 200ms），
 * 请求级 timeoutMs 可进一步收窄（两者取小），超时后脚本线程被中断，fail-safe 拒绝。
 *
 * 引擎持有编译缓存与隔离类加载器，容器关闭时整体释放（destroyMethod="close"）。
 */
@Configuration(proxyBeanMethods = false, value = "decisionEngineConfiguration")
class EngineConfiguration {
    @Bean(destroyMethod = "close")
    fun decisionScriptEngine(properties: DecisionProperties): GroovyScriptEngine =
        GroovyScriptEngine(
            defaultExecutionTimeout = Duration.ofMillis(properties.executionTimeoutMs),
        )
}
