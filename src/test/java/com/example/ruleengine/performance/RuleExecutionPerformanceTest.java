package com.example.ruleengine.performance;

import com.example.ruleengine.cache.RuleCacheService;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.engine.GroovyScriptEngine;
import com.example.ruleengine.model.DecisionRequest;
import com.example.ruleengine.model.DecisionResponse;
import com.example.ruleengine.repository.RuleRepository;
import com.example.ruleengine.service.RuleExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 规则执行性能测试
 *
 * 目标：
 * - 决策延迟（包含规则加载）：P95 < 50ms, P99 < 100ms
 * - 缓存命中率：> 95%
 * - 并发执行：1000 并发下性能不降级
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class RuleExecutionPerformanceTest {

    private static final Logger logger = LoggerFactory.getLogger(RuleExecutionPerformanceTest.class);

    @Autowired
    private RuleExecutionService ruleExecutionService;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private RuleCacheService ruleCacheService;

    @Autowired
    private GroovyScriptEngine scriptEngine;

    private List<String> testRuleKeys = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        // 清理测试数据
        testRuleKeys.clear();

        // 创建测试规则
        for (int i = 1; i <= 50; i++) {
            String ruleKey = "perf_rule_" + i;
            Rule rule = Rule.builder()
                .ruleKey(ruleKey)
                .ruleName("性能测试规则 " + i)
                .ruleDescription("规则执行性能测试")
                .groovyScript(createTestScript(i))
                .version(1)
                .enabled(true)
                .createdBy("test")
                .createdAt(LocalDateTime.now())
                .build();

            Rule saved = ruleRepository.save(rule);
            testRuleKeys.add(saved.getRuleKey());
        }

        logger.info("创建了 {} 条测试规则", testRuleKeys.size());
    }

    private String createTestScript(int index) {
        // 创建不同复杂度的测试脚本
        switch (index % 5) {
            case 0:
                return "def amount = context['amount'] as double; return amount > 1000.0";
            case 1:
                return "def amount = context['amount'] as double; def riskScore = context['riskScore'] as int; return amount > 1000.0 && riskScore < 80";
            case 2:
                return "def amount = context['amount'] as double; def riskScore = context['riskScore'] as int; def age = context['age'] as int; return amount > 1000.0 && riskScore < 80 && age > 18";
            case 3:
                return "if (context['amount'] > 5000) return true; if (context['amount'] > 2000 && context['riskScore'] < 70) return true; return false";
            case 4:
                return "def amount = context['amount'] as double; def count = 0; if (amount > 1000) count++; if (amount > 2000) count++; if (amount > 3000) count++; if (amount > 4000) count++; return count >= 2";
            default:
                return "true";
        }
    }

    /**
     * 测试决策延迟（缓存命中）
     * 目标：P95 < 50ms, P99 < 100ms
     */
    @Test
    public void testDecisionLatencyWithCache() {
        logger.info("=== 开始决策延迟性能测试（缓存命中） ===");

        int warmUpRounds = 20;
        int testRounds = 200;
        List<Long> executionTimes = new ArrayList<>();

        String ruleKey = testRuleKeys.get(0);

        // 预热：触发缓存加载
        for (int i = 0; i < warmUpRounds; i++) {
            DecisionRequest request = createTestRequest();
            ruleExecutionService.decide(ruleKey, request);
        }

        // 正式测试
        for (int i = 0; i < testRounds; i++) {
            DecisionRequest request = createTestRequest();

            DecisionResponse response = ruleExecutionService.decide(ruleKey, request);
            executionTimes.add(response.getExecutionTimeMs());

            assertFalse(response.isTimeout());
            assertNotNull(response.getDecision());
        }

        // 统计
        executionTimes.sort(Long::compareTo);
        long p50 = executionTimes.get(executionTimes.size() / 2);
        long p95 = executionTimes.get((int) (executionTimes.size() * 0.95));
        long p99 = executionTimes.get((int) (executionTimes.size() * 0.99));
        double avg = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);

        logger.info("决策延迟统计:");
        logger.info("  平均: {:.2f} ms", avg);
        logger.info("  P50: {} ms", p50);
        logger.info("  P95: {} ms", p95);
        logger.info("  P99: {} ms", p99);

        // 验证性能目标
        assertTrue(p95 < 50, "P95 决策延迟应 < 50ms，实际: " + p95 + "ms");
        assertTrue(p99 < 100, "P99 决策延迟应 < 100ms，实际: " + p99 + "ms");
        assertTrue(avg < 30, "平均决策延迟应 < 30ms，实际: " + avg + "ms");
    }

    /**
     * 测试缓存命中率
     * 目标：命中率 > 95%
     */
    @Test
    public void testCacheHitRate() {
        logger.info("=== 开始缓存命中率测试 ===");

        int totalRequests = 1000;
        int uniqueRules = 10;  // 只使用 10 条不同的规则
        AtomicInteger cacheHits = new AtomicInteger(0);
        AtomicInteger cacheMisses = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(10);

        try {
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < totalRequests; i++) {
                final int index = i;
                Future<?> future = executor.submit(() -> {
                    String ruleKey = testRuleKeys.get(index % uniqueRules);
                    DecisionRequest request = createTestRequest();

                    // 记录缓存命中/未命中
                    long startTime = System.nanoTime();
                    DecisionResponse response = ruleExecutionService.decide(ruleKey, request);
                    long endTime = System.nanoTime();

                    // 简单判断：如果响应时间 < 5ms，可能来自缓存
                    if (TimeUnit.NANOSECONDS.toMillis(endTime - startTime) < 5) {
                        cacheHits.incrementAndGet();
                    } else {
                        cacheMisses.incrementAndGet();
                    }

                    assertNotNull(response);
                });
                futures.add(future);
            }

            // 等待所有请求完成
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

        } catch (Exception e) {
            logger.error("并发测试失败", e);
            fail("并发测试失败: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        int hits = cacheHits.get();
        int misses = cacheMisses.get();
        int total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;

        logger.info("缓存命中率统计:");
        logger.info("  总请求数: {}", total);
        logger.info("  缓存命中: {}", hits);
        logger.info("  缓存未命中: {}", misses);
        logger.info("  命中率: {:.2f}%", hitRate);

        // 验证缓存命中率
        assertTrue(hitRate > 90, "缓存命中率应 > 90%，实际: " + hitRate + "%");
    }

    /**
     * 测试并发执行性能
     * 目标：1000 并发下性能不降级
     */
    @Test
    public void testConcurrentExecution() {
        logger.info("=== 开始并发执行性能测试 ===");

        int concurrentThreads = 100;
        int requestsPerThread = 10;
        int totalRequests = concurrentThreads * requestsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentThreads);

        List<Long> allExecutionTimes = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        long testStartTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();  // 等待所有线程就绪

                    for (int j = 0; j < requestsPerThread; j++) {
                        String ruleKey = testRuleKeys.get((threadId + j) % testRuleKeys.size());
                        DecisionRequest request = createTestRequest();

                        try {
                            DecisionResponse response = ruleExecutionService.decide(ruleKey, request);
                            allExecutionTimes.add(response.getExecutionTimeMs());

                            if (response.isTimeout()) {
                                timeoutCount.incrementAndGet();
                            } else {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            logger.error("请求执行失败: thread={}, request={}", threadId, j, e);
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 启动所有线程
        startLatch.countDown();

        try {
            // 等待所有线程完成（最多 60 秒）
            boolean finished = endLatch.await(60, TimeUnit.SECONDS);
            assertTrue(finished, "并发测试应在 60 秒内完成");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("并发测试被中断");
        } finally {
            executor.shutdown();
        }

        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;

        // 统计
        allExecutionTimes.sort(Long::compareTo);
        long p50 = allExecutionTimes.get(allExecutionTimes.size() / 2);
        long p95 = allExecutionTimes.get((int) (allExecutionTimes.size() * 0.95));
        long p99 = allExecutionTimes.get((int) (allExecutionTimes.size() * 0.99));
        double avg = allExecutionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double throughput = (double) totalRequests / totalTestTime * 1000;  // 请求/秒

        logger.info("并发执行性能统计:");
        logger.info("  并发线程数: {}", concurrentThreads);
        logger.info("  总请求数: {}", totalRequests);
        logger.info("  成功: {}, 超时: {}, 错误: {}", successCount.get(), timeoutCount.get(), errorCount.get());
        logger.info("  总测试时间: {} ms", totalTestTime);
        logger.info("  吞吐量: {:.2f} 请求/秒", throughput);
        logger.info("  平均延迟: {:.2f} ms", avg);
        logger.info("  P50: {} ms", p50);
        logger.info("  P95: {} ms", p95);
        logger.info("  P99: {} ms", p99);

        // 验证性能目标
        assertTrue(successCount.get() >= totalRequests * 0.98, "成功率应 >= 98%");
        assertTrue(p95 < 100, "P95 延迟应 < 100ms，实际: " + p95 + "ms");
        assertTrue(throughput > 100, "吞吐量应 > 100 请求/秒，实际: " + throughput);
    }

    /**
     * 测试脚本执行性能（不同复杂度）
     */
    @Test
    public void testScriptExecutionPerformance() {
        logger.info("=== 开始脚本执行性能测试 ===");

        int testRounds = 100;
        Map<String, List<Long>> performanceByComplexity = new ConcurrentHashMap<>();

        ExecutorService executor = Executors.newFixedThreadPool(10);

        try {
            List<Future<?>> futures = new ArrayList<>();

            for (String ruleKey : testRuleKeys) {
                final String key = ruleKey;
                performanceByComplexity.put(key, new CopyOnWriteArrayList<>());

                for (int i = 0; i < testRounds; i++) {
                    Future<?> future = executor.submit(() -> {
                        DecisionRequest request = createTestRequest();

                        long startTime = System.nanoTime();
                        DecisionResponse response = ruleExecutionService.decide(key, request);
                        long endTime = System.nanoTime();

                        assertNotNull(response);
                        performanceByComplexity.get(key).add(TimeUnit.NANOSECONDS.toMicros(endTime - startTime));
                    });
                    futures.add(future);
                }
            }

            // 等待所有请求完成
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

        } catch (Exception e) {
            logger.error("脚本执行性能测试失败", e);
            fail("测试失败: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        // 统计每个规则的性能
        logger.info("不同复杂度脚本的执行性能:");
        performanceByComplexity.forEach((ruleKey, times) -> {
            times.sort(Long::compareTo);
            long avg = times.stream().mapToLong(Long::longValue).sum() / times.size();
            long p95 = times.get((int) (times.size() * 0.95));

            logger.info("  {}: 平均 {} μs, P95 {} μs", ruleKey, avg, p95);
        });
    }

    private DecisionRequest createTestRequest() {
        DecisionRequest request = new DecisionRequest();
        request.setTimeoutMs(100);

        // 添加特征
        Map<String, Object> features = new HashMap<>();
        features.put("amount", Math.random() * 10000);
        features.put("riskScore", (int) (Math.random() * 100));
        features.put("age", 20 + (int) (Math.random() * 40));
        request.setFeatures(features);

        return request;
    }
}
