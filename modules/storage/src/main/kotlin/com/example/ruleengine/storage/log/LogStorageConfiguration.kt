package com.example.ruleengine.storage.log

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 执行日志仓储装配：显式 @Bean 注册（与 [StorageConfigurationKt] 的仓储装配风格一致）。
 *
 * 不使用 @Repository 组件扫描：Exposed 实现为 final class（storage 库模块不经
 * kotlin-allopen），注解驱动的代理机制会要求生成 CGLIB 子类而失败。
 */
@Configuration(proxyBeanMethods = false)
class LogStorageConfiguration {
    @Bean
    fun executionLogRepository(): ExecutionLogRepository = ExposedExecutionLogRepository()

    @Bean
    fun canaryExecutionLogRepository(): CanaryExecutionLogRepository = ExposedCanaryExecutionLogRepository()
}
