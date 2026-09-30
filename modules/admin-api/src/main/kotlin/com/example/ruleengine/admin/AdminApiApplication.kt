package com.example.ruleengine.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 管理服务入口。
 *
 * 职责：规则 CRUD、版本管理、灰度发布、特征目录、AI 规则生成、审计。
 * 与决策服务物理隔离，管理操作不影响 50ms 决策链路（阶段 2 实现业务）。
 */
@SpringBootApplication
class AdminApiApplication

fun main(args: Array<String>) {
    runApplication<AdminApiApplication>(*args)
}
