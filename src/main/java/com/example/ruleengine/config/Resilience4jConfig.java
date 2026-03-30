package com.example.ruleengine.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Resilience4j 配置
 * Source: RESEARCH.md 模式3 + CONTEXT.md 决策 D-11、D-12
 *
 * 功能：
 * 1. D-11: 配置 50ms 超时控制
 * 2. D-12: 配置独立线程池执行规则，隔离风险
 * 3. 防止单个规则执行影响整体性能
 */
@Configuration
public class Resilience4jConfig {

    /**
     * 规则执行 TimeLimiter
     * D-11: 50ms 超时配置
     */
    @Bean
    public TimeLimiter ruleExecutionTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(50))
            .build();

        return TimeLimiter.of(config);
    }

    /**
     * 规则执行独立线程池
     * D-12: 使用独立线程池执行规则，隔离风险
     *
     * 配置说明：
     * - 核心线程数：CPU核心数 * 2（充分利用多核）
     * - 最大线程数：核心线程数 * 2（突发流量处理）
     * - 队列容量：100（缓冲等待任务）
     * - 线程名前缀：rule-executor-（便于日志追踪）
     * - 拒绝策略：CallerRunsPolicy（确保不会抛出拒绝异常，由调用线程执行）
     */
    @Bean(name = "ruleExecutorPool")
    public ThreadPoolTaskExecutor ruleExecutorPool() {
        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        int maxPoolSize = corePoolSize * 2;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("rule-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        return executor;
    }

    /**
     * 规则执行 CircuitBreaker
     * REXEC-05: 断路器保护，防止级联故障
     *
     * 配置说明：
     * - 滑动窗口大小：100（基于计数的滑动窗口）
     * - 失败率阈值：50%（超过则打开断路器）
     * - 等待时间：10s（断路器打开后等待 10 秒进入半开状态）
     * - 半开状态允许请求数：10
     * - 最小请求数：20（达到后才开始计算失败率）
     */
    @Bean(name = "ruleExecutionCircuitBreaker")
    public CircuitBreaker ruleExecutionCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(100)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .permittedNumberOfCallsInHalfOpenState(10)
            .minimumNumberOfCalls(20)
            .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("ruleExecution", config);

        // 断路器事件监听
        circuitBreaker.getEventPublisher()
            .onStateTransition(event ->
                org.slf4j.LoggerFactory.getLogger(Resilience4jConfig.class)
                    .warn("CircuitBreaker 状态变更: {}", event))
            .onError(event ->
                org.slf4j.LoggerFactory.getLogger(Resilience4jConfig.class)
                    .debug("CircuitBreaker 记录错误: {}", event))
            .onSuccess(event ->
                org.slf4j.LoggerFactory.getLogger(Resilience4jConfig.class)
                    .debug("CircuitBreaker 记录成功: {}", event));

        return circuitBreaker;
    }
}
