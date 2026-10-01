package com.eatmoreduck.ruleengine.decision.config

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.springframework.stereotype.Component

/**
 * 应用级协程作用域：后台刷盘协程、异步决策执行、结果过期清理等长生命周期协程的父作用域。
 *
 * SupervisorJob：单个子协程失败不影响兄弟协程；容器关闭时统一 cancel。
 */
@Component
class ApplicationCoroutineScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("decision-api")) {
    @PreDestroy
    fun shutdown() {
        cancel("应用关闭")
    }
}
