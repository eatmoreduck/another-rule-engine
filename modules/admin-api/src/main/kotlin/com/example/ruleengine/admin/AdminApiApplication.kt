package com.example.ruleengine.admin

import com.example.ruleengine.storage.log.ExposedCanaryExecutionLogRepository
import com.example.ruleengine.storage.log.ExposedExecutionLogRepository
import com.example.ruleengine.storage.repository.ExposedDecisionFlowRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * 管理服务入口。
 *
 * 职责：规则 CRUD、版本管理、灰度发布、特征目录、AI 规则生成、审计。
 * 与决策服务物理隔离，管理操作不影响 50ms 决策链路（阶段 2 实现业务）。
 *
 * 组件扫描扩大到 com.example.ruleengine 根包：storage 模块的 [StorageConfiguration]
 * （数据源/Exposed/Flyway/事务管理器/四个仓储）不在 admin 包下，需显式纳入扫描。
 *
 * 排除三个 final @Repository 类的扫描注册：storage 为 kotlin-library 约定（无
 * kotlin-spring all-open），final @Repository 类被扫描注册后
 * PersistenceExceptionTranslationPostProcessor 会按实例具体类生成 CGLIB 代理，
 * final 类无法子类化 → 上下文启动失败（"Could not generate CGLIB subclass"）。
 * 三者改由 [com.example.ruleengine.admin.config.StorageRepositoryConfiguration]
 * 以 open 委托类按接口类型显式注册。
 */
@SpringBootApplication
@ComponentScan(
    basePackages = ["com.example.ruleengine"],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ExposedDecisionFlowRepository::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ExposedExecutionLogRepository::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ExposedCanaryExecutionLogRepository::class]),
    ],
)
class AdminApiApplication

fun main(args: Array<String>) {
    runApplication<AdminApiApplication>(*args)
}
