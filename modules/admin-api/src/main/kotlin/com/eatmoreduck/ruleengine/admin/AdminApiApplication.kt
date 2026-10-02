package com.eatmoreduck.ruleengine.admin

import com.eatmoreduck.ruleengine.storage.log.ExposedCanaryExecutionLogRepository
import com.eatmoreduck.ruleengine.storage.log.ExposedExecutionLogRepository
import com.eatmoreduck.ruleengine.storage.repository.ExposedDecisionFlowRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * 统一部署物入口（管理面 + 决策面）。
 *
 * 职责：规则 CRUD、版本管理、灰度发布、特征目录、AI 规则生成、审计，
 * 以及决策链路（决策请求 -> 版本钉住/灰度分流 -> 并发取特征 -> 沙箱执行 -> 决策结果）。
 *
 * 2026-10 合并说明：decision-api 不再是独立部署物，其组件（com.eatmoreduck.ruleengine.decision）
 * 经 classpath 由本类根包扫描吸入同一上下文；决策链路仍保持无状态设计，将来可拆回独立部署物
 * （为 decision-api 加回启动类与配置即可，模块/包边界未动）。
 *
 * 组件扫描范围为 com.eatmoreduck.ruleengine 根包：storage 模块的 [StorageConfiguration]
 * （数据源/Exposed/Flyway/事务管理器/仓储）与 decision 包的决策组件不在 admin 包下，一并纳入。
 *
 * 排除三个 final @Repository 类的扫描注册：storage 为 kotlin-library 约定（无
 * kotlin-spring all-open），final @Repository 类被扫描注册后
 * PersistenceExceptionTranslationPostProcessor 会按实例具体类生成 CGLIB 代理，
 * final 类无法子类化 → 上下文启动失败（"Could not generate CGLIB subclass"）。
 * 三者改由 [com.eatmoreduck.ruleengine.admin.config.StorageRepositoryConfiguration]
 * 以 open 委托类按接口类型显式注册（decision 包组件经 spring-library 约定保持 open，无此问题）。
 */
@SpringBootApplication
@ComponentScan(
    basePackages = ["com.eatmoreduck.ruleengine"],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ExposedDecisionFlowRepository::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ExposedExecutionLogRepository::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ExposedCanaryExecutionLogRepository::class]),
    ],
)
@ConfigurationPropertiesScan(basePackages = ["com.eatmoreduck.ruleengine.decision"])
class AdminApiApplication

fun main(args: Array<String>) {
    runApplication<AdminApiApplication>(*args)
}
