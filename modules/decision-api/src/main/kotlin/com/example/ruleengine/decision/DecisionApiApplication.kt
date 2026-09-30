package com.example.ruleengine.decision

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 决策服务入口。
 *
 * 职责：接收决策请求 -> 加载特征 -> 执行规则 -> 50ms 内返回决策结果。
 * 阶段 3 实现决策链路，当前仅保证应用可启动、探针可用。
 */
@SpringBootApplication
class DecisionApiApplication

fun main(args: Array<String>) {
    runApplication<DecisionApiApplication>(*args)
}
