package com.example.ruleengine.admin.config

import com.example.ruleengine.storage.repository.DecisionFlowRepository
import com.example.ruleengine.storage.repository.ExposedDecisionFlowRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** [ExposedDecisionFlowRepository] 的 open 委托包装 */
class OpenDecisionFlowRepository(
    private val delegate: ExposedDecisionFlowRepository,
) : DecisionFlowRepository by delegate

/**
 * storage 模块 final @Repository 仓储的显式注册（决策流一族）。
 *
 * 为什么不直接把 storage 实现类注册为 bean：ExposedDecisionFlowRepository 标注了
 * @Repository 且是 final 类（storage 为 kotlin-library 约定，无 kotlin-spring
 * all-open）。无论经组件扫描还是 @Bean 注册，PersistenceExceptionTranslation
 * PostProcessor 都会为 @Repository bean 按实例的具体类生成 CGLIB 代理，final 类
 * 无法子类化 → 上下文启动失败（"Could not generate CGLIB subclass"）。
 *
 * 处理：扫描排除（见 AdminApiApplication）+ 本模块 open 委托类包装注册。
 *
 * 注意：执行日志两族仓储（ExecutionLog/CanaryExecutionLog）**不在这里注册**——
 * 它们的 storage 实现（storage.log 包）本身不带 @Repository，由 storage 的
 * LogStorageConfiguration 统一 @Bean 注册，本模块重复注册会触发
 * BeanDefinitionOverrideException（两配置类在根包扫描下同时生效）。
 * 事务语义不变：仓储本身不开事务，边界由调用方声明。
 */
@Configuration(proxyBeanMethods = false)
class StorageRepositoryConfiguration {
    @Bean
    fun decisionFlowRepository(): DecisionFlowRepository = OpenDecisionFlowRepository(ExposedDecisionFlowRepository())
}
