package com.example.ruleengine.logconsumer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 日志消费服务入口。
 *
 * 职责：消费 Kafka 决策事件流，批量落库（表按时间分区）。
 * 按 consumer lag 扩缩容（阶段 4 实现）。
 */
@SpringBootApplication
class LogConsumerApplication

fun main(args: Array<String>) {
    runApplication<LogConsumerApplication>(*args)
}
