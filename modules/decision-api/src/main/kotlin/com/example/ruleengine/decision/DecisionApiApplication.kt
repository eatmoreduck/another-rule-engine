package com.example.ruleengine.decision

import com.example.ruleengine.storage.repository.ExposedDecisionFlowRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * 决策服务入口（部署物 1）。
 *
 * 职责：接收决策请求 -> 版本钉住/灰度分流 -> 并发取特征 -> 沙箱执行 -> 50ms 内返回决策结果。
 * 决策节点完全无状态，可水平扩缩（HPA 按 QPS）。
 *
 * 组件扫描范围刻意收窄为 decision + storage 两包：
 * - storage 的 [com.example.ruleengine.storage.config.StorageConfiguration]（数据源/Exposed/Flyway/
 *   事务管理器/仓储）与日志装配 [com.example.ruleengine.storage.log.LogStorageConfiguration] 需纳入；
 * - domain/dsl/engine/shared 为零框架依赖的纯库，无组件可扫；
 * - admin-api（com.example.ruleengine.admin）是另一部署物的组件集，严禁吸入（会拉入其控制器/认证/配置）。
 *
 * 排除 ExposedDecisionFlowRepository：该 bean 为 final class 且标注 @Repository，注解驱动的
 * 代理（PersistenceExceptionTranslation 后处理）会要求生成 CGLIB 子类而失败；决策执行侧的
 * 流读取由 decision-api 自持的 [com.example.ruleengine.decision.repo.ExposedDecisionFlowReader] 承担。
 */
@SpringBootApplication
@ComponentScan(
    basePackages = ["com.example.ruleengine.decision", "com.example.ruleengine.storage"],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ExposedDecisionFlowRepository::class]),
    ],
)
@ConfigurationPropertiesScan(basePackages = ["com.example.ruleengine.decision"])
class DecisionApiApplication

fun main(args: Array<String>) {
    runApplication<DecisionApiApplication>(*args)
}
