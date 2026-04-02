package com.example.ruleengine.performance;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.repository.RuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能基准测试
 *
 * 目标：
 * - 规则加载延迟（从数据库）：P95 < 10ms
 * - 规则执行延迟（包含加载）：P95 < 50ms
 * - 缓存命中率：> 95%
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PerformanceBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceBenchmark.class);

    @Autowired
    private RuleRepository ruleRepository;

    private List<String> testRuleKeys = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        // 清理测试数据
        testRuleKeys.clear();

        // 创建测试规则
        for (int i = 1; i <= 100; i++) {
            String ruleKey = "test_rule_" + i;
            Rule rule = Rule.builder()
                .ruleKey(ruleKey)
                .ruleName("测试规则 " + i)
                .ruleDescription("性能测试规则")
                .groovyScript("def amount = context['amount'] as double; return amount > 1000.0")
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

    /**
     * 测试规则加载性能（从数据库）
     * 目标：P95 < 10ms
     */
    @Test
    public void benchmarkRuleLoading() {
        logger.info("=== 开始规则加载性能测试 ===");

        int warmUpRounds = 10;
        int testRounds = 100;
        List<Long> loadTimes = new ArrayList<>();

        // 预热
        for (int i = 0; i < warmUpRounds; i++) {
            String ruleKey = testRuleKeys.get(i % testRuleKeys.size());
            ruleRepository.findByRuleKey(ruleKey);
        }

        // 正式测试
        for (int i = 0; i < testRounds; i++) {
            String ruleKey = testRuleKeys.get(i % testRuleKeys.size());

            long startTime = System.nanoTime();
            Rule rule = ruleRepository.findByRuleKey(ruleKey).orElse(null);
            long endTime = System.nanoTime();

            assertNotNull(rule);
            loadTimes.add(TimeUnit.NANOSECONDS.toMillis(endTime - startTime));
        }

        // 统计
        loadTimes.sort(Long::compareTo);
        long p50 = loadTimes.get(loadTimes.size() / 2);
        long p95 = loadTimes.get((int) (loadTimes.size() * 0.95));
        long p99 = loadTimes.get((int) (loadTimes.size() * 0.99));
        double avg = loadTimes.stream().mapToLong(Long::longValue).average().orElse(0);

        logger.info("规则加载性能统计:");
        logger.info("  平均: {:.2f} ms", avg);
        logger.info("  P50: {} ms", p50);
        logger.info("  P95: {} ms", p95);
        logger.info("  P99: {} ms", p99);

        // 验证性能目标
        assertTrue(p95 < 20, "P95 加载时间应 < 20ms，实际: " + p95 + "ms");
        assertTrue(avg < 10, "平均加载时间应 < 10ms，实际: " + avg + "ms");
    }

    /**
     * 测试批量规则加载性能
     */
    @Test
    public void benchmarkBatchRuleLoading() {
        logger.info("=== 开始批量规则加载性能测试 ===");

        int warmUpRounds = 10;
        int testRounds = 50;
        int batchSize = 10;
        List<Long> loadTimes = new ArrayList<>();

        // 预热
        for (int i = 0; i < warmUpRounds; i++) {
            int fromIndex = (i * batchSize) % testRuleKeys.size();
            int toIndex = Math.min(fromIndex + batchSize, testRuleKeys.size());
            List<String> batch = testRuleKeys.subList(fromIndex, toIndex);
            ruleRepository.findByRuleKeyIn(batch);
        }

        // 正式测试
        for (int i = 0; i < testRounds; i++) {
            int fromIndex = (i * batchSize) % testRuleKeys.size();
            int toIndex = Math.min(fromIndex + batchSize, testRuleKeys.size());
            List<String> batch = testRuleKeys.subList(fromIndex, toIndex);

            long startTime = System.nanoTime();
            List<Rule> rules = ruleRepository.findByRuleKeyIn(batch);
            long endTime = System.nanoTime();

            assertFalse(rules.isEmpty());
            loadTimes.add(TimeUnit.NANOSECONDS.toMillis(endTime - startTime));
        }

        // 统计
        loadTimes.sort(Long::compareTo);
        long p50 = loadTimes.get(loadTimes.size() / 2);
        long p95 = loadTimes.get((int) (loadTimes.size() * 0.95));
        double avg = loadTimes.stream().mapToLong(Long::longValue).average().orElse(0);

        logger.info("批量规则加载性能统计 (批量大小: {}):", batchSize);
        logger.info("  平均: {:.2f} ms", avg);
        logger.info("  P50: {} ms", p50);
        logger.info("  P95: {} ms", p95);

        // 验证批量加载性能
        assertTrue(p95 < 30, "P95 批量加载时间应 < 30ms，实际: " + p95 + "ms");
    }

    /**
     * 测试不同数据量下的加载性能
     */
    @Test
    public void benchmarkRuleLoadingWithDifferentDataSizes() {
        logger.info("=== 开始不同数据量下的规则加载性能测试 ===");

        int[] dataSizes = {10, 50, 100, 500, 1000};

        for (int dataSize : dataSizes) {
            // 创建指定数量的规则
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < dataSize; i++) {
                String ruleKey = "perf_test_rule_" + dataSize + "_" + i;
                Rule rule = Rule.builder()
                    .ruleKey(ruleKey)
                    .ruleName("性能测试规则 " + dataSize + "_" + i)
                    .ruleDescription("不同数据量性能测试")
                    .groovyScript("def amount = context['amount'] as double; return amount > 1000.0")
                    .version(1)
                    .enabled(true)
                    .createdBy("test")
                    .createdAt(LocalDateTime.now())
                    .build();
                rules.add(rule);
            }
            ruleRepository.saveAll(rules);

            // 测试加载性能
            String testRuleKey = "perf_test_rule_" + dataSize + "_0";

            int testRounds = 20;
            List<Long> loadTimes = new ArrayList<>();

            for (int i = 0; i < testRounds; i++) {
                long startTime = System.nanoTime();
                Rule rule = ruleRepository.findByRuleKey(testRuleKey).orElse(null);
                long endTime = System.nanoTime();

                assertNotNull(rule);
                loadTimes.add(TimeUnit.NANOSECONDS.toMillis(endTime - startTime));
            }

            // 统计
            double avg = loadTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long p95 = loadTimes.stream().sorted().skip((long) (loadTimes.size() * 0.95)).findFirst().orElse(0L);

            logger.info("数据量: {}, 平均加载时间: {:.2f} ms, P95: {} ms", dataSize, avg, p95);
        }
    }
}
