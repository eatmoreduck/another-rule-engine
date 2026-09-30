package com.example.ruleengine.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 管理服务入口。
 *
 * 职责：规则 CRUD、版本管理、灰度发布、特征目录、AI 规则生成、审计。
 * 与决策服务物理隔离，管理操作不影响 50ms 决策链路（阶段 2 实现业务）。
 *
 * 组件扫描扩大到 com.example.ruleengine 根包：storage 模块的 [StorageConfiguration]
 * （数据源/Exposed/Flyway/事务管理器/四个仓储）不在 admin 包下，需显式纳入扫描。
 */
@SpringBootApplication(scanBasePackages = ["com.example.ruleengine"])
class AdminApiApplication

fun main(args: Array<String>) {
    runApplication<AdminApiApplication>(*args)
}
