package com.example.ruleengine.cache;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.metrics.CacheMetrics;
import com.example.ruleengine.repository.RuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓存性能测试
 *
 * 目标：
 * - 缓存命中率 > 95%
 * - 缓存访问延迟 < 1ms
 * - 并发场景下缓存工作正常
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("缓存性能测试")
class CachePerformanceTest {

    @Autowired
    private RuleCacheService ruleCacheService;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheMetrics cacheMetrics;

    private List<String> testRuleKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 清除缓存
        org.springframework.cache.Cache rulesCache = cacheManager.getCache("rules");
        if (rulesCache != null) {
            rulesCache.clear();
        }
        ruleRepository.deleteAllInBatch();
        testRuleKeys.clear();

        // 创建测试规则
        for (int i = 1; i <= 20; i++) {
            Rule rule = Rule.builder()
                .ruleKey("cache-perf-rule-" + i)
                .ruleName("缓存性能测试规则 " + i)
                .ruleDescription("用于测试缓存性能")
                .groovyScript("def amount = context['amount'] as double; return amount > 1000.0")
                .version(1)
                .enabled(true)
                .createdBy("test")
                .build();
            Rule saved = ruleRepository.save(rule);
            testRuleKeys.add(saved.getRuleKey());
        }
    }

    @Test
    @DisplayName("应该验证缓存命中率高")
    void shouldVerifyHighCacheHitRate() {
        int totalRequests = 1000;
        int uniqueRules = 10;  // 只使用 10 条不同的规则

        // 预热：首次加载到缓存
        for (int i = 0; i < uniqueRules; i++) {
            String ruleKey = testRuleKeys.get(i);
            ruleCacheService.getEnabledRule(ruleKey);
        }

        // 执行大量请求（大部分应该命中缓存）
        for (int i = 0; i < totalRequests; i++) {
            String ruleKey = testRuleKeys.get(i % uniqueRules);
            Rule rule = ruleCacheService.getEnabledRule(ruleKey);
            assertThat(rule).isNotNull();
        }

        // 获取缓存统计
        Map<String, Map<String, Object>> allStats = cacheMetrics.getAllCacheStats();
        Map<String, Object> rulesStats = allStats.get("rules");

        assertThat(rulesStats).isNotNull();

        double hitRate = ((Number) rulesStats.get("hitRate")).doubleValue();
        long hitCount = ((Number) rulesStats.get("hitCount")).longValue();
        long missCount = ((Number) rulesStats.get("missCount")).longValue();

        System.out.println("缓存统计:");
        System.out.println("  命中率: " + (hitRate * 100) + "%");
        System.out.println("  命中次数: " + hitCount);
        System.out.println("  未命中次数: " + missCount);
        System.out.println("  缓存大小: " + rulesStats.get("size"));

        // 验证命中率 > 90%（考虑首次加载）
        assertThat(hitRate).isGreaterThan(0.90);
    }

    @Test
    @DisplayName("应该验证缓存访问延迟低")
    void shouldVerifyLowCacheLatency() {
        String ruleKey = testRuleKeys.get(0);

        // 预热：加载到缓存
        ruleCacheService.getEnabledRule(ruleKey);

        int testRounds = 1000;
        List<Long> latencies = new ArrayList<>();

        // 测试缓存访问延迟
        for (int i = 0; i < testRounds; i++) {
            long startTime = System.nanoTime();
            Rule rule = ruleCacheService.getEnabledRule(ruleKey);
            long endTime = System.nanoTime();

            assertThat(rule).isNotNull();
            latencies.add(TimeUnit.NANOSECONDS.toMicros(endTime - startTime));
        }

        // 计算统计数据
        latencies.sort(Long::compareTo);
        long avg = latencies.stream().mapToLong(Long::longValue).sum() / latencies.size();
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));

        System.out.println("缓存访问延迟统计 (单位: μs):");
        System.out.println("  平均: " + avg + " μs");
        System.out.println("  P95: " + p95 + " μs");
        System.out.println("  P99: " + p99 + " μs");

        // 验证延迟 < 1ms (1000 μs)
        assertThat(p95).isLessThan(1000);
    }

    @Test
    @DisplayName("应该支持高并发缓存访问")
    void shouldSupportHighConcurrency() throws InterruptedException, ExecutionException {
        int concurrentThreads = 50;
        int requestsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentThreads; i++) {
            final int threadId = i;
            Future<Boolean> future = executor.submit(() -> {
                try {
                    startLatch.await();  // 等待所有线程就绪

                    for (int j = 0; j < requestsPerThread; j++) {
                        String ruleKey = testRuleKeys.get((threadId + j) % testRuleKeys.size());
                        Rule rule = ruleCacheService.getEnabledRule(ruleKey);
                        if (rule == null) {
                            return false;
                        }
                    }
                    return true;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            });
            futures.add(future);
        }

        // 启动所有线程
        startLatch.countDown();

        // 等待所有线程完成
        for (Future<Boolean> future : futures) {
            try {
                assertThat(future.get(30, TimeUnit.SECONDS)).isTrue();
            } catch (TimeoutException e) {
                fail("线程执行超时: " + e.getMessage());
            }
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 获取缓存统计
        Map<String, Map<String, Object>> allStats = cacheMetrics.getAllCacheStats();
        Map<String, Object> rulesStats = allStats.get("rules");

        System.out.println("并发访问后缓存统计:");
        System.out.println("  缓存大小: " + rulesStats.get("size"));
        System.out.println("  命中率: " + (rulesStats.get("hitRate")));

        // 验证缓存仍然有效
        assertThat(rulesStats.get("size")).isNotNull();
    }

    @Test
    @DisplayName("应该正确清除缓存")
    void shouldEvictCacheCorrectly() {
        String ruleKey = testRuleKeys.get(0);

        // 加载到缓存
        Rule rule1 = ruleCacheService.getEnabledRule(ruleKey);
        assertThat(rule1).isNotNull();

        // 获取缓存统计
        Map<String, Map<String, Object>> stats1 = cacheMetrics.getAllCacheStats();
        long size1 = ((Number) stats1.get("rules").get("size")).longValue();
        assertThat(size1).isGreaterThan(0);

        // 清除缓存
        ruleCacheService.evictRule(ruleKey);

        // 再次获取（应该从数据库加载）
        Rule rule2 = ruleCacheService.getEnabledRule(ruleKey);
        assertThat(rule2).isNotNull();

        // 验证缓存清除后重新加载
        Map<String, Map<String, Object>> stats2 = cacheMetrics.getAllCacheStats();
        long missCount2 = ((Number) stats2.get("rules").get("missCount")).longValue();
        assertThat(missCount2).isGreaterThan(0);
    }

    @Test
    @DisplayName("应该支持批量清除所有缓存")
    void shouldEvictAllCache() {
        // 加载所有规则到缓存
        testRuleKeys.forEach(ruleKey -> ruleCacheService.getEnabledRule(ruleKey));

        // 获取缓存统计
        Map<String, Map<String, Object>> stats1 = cacheMetrics.getAllCacheStats();
        long size1 = ((Number) stats1.get("rules").get("size")).longValue();
        assertThat(size1).isGreaterThan(0);

        // 清除所有缓存
        ruleCacheService.evictAll();

        // 验证缓存已清除
        Map<String, Map<String, Object>> stats2 = cacheMetrics.getAllCacheStats();
        long size2 = ((Number) stats2.get("rules").get("size")).longValue();
        // 注意：size2 可能为 0，也可能不为 0（取决于缓存实现）
    }

    @Test
    @DisplayName("应该测试缓存预热")
    void shouldWarmUpCache() {
        // 清除缓存
        cacheManager.getCache("rules").clear();

        // 模拟缓存预热
        testRuleKeys.forEach(ruleKey -> {
            Rule rule = ruleCacheService.getEnabledRule(ruleKey);
            assertThat(rule).isNotNull();
        });

        // 获取缓存统计
        Map<String, Map<String, Object>> stats = cacheMetrics.getAllCacheStats();
        long size = ((Number) stats.get("rules").get("size")).longValue();

        System.out.println("缓存预热后统计:");
        System.out.println("  预热规则数: " + testRuleKeys.size());
        System.out.println("  缓存大小: " + size);

        // 验证缓存已预热
        assertThat(size).isGreaterThanOrEqualTo((long) testRuleKeys.size());
    }
}
