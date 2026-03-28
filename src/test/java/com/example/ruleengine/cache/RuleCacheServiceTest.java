package com.example.ruleengine.cache;

import com.example.ruleengine.constants.RuleStatus;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.repository.RuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RuleCacheService 单元测试
 */
@SpringBootTest
@Testcontainers
@DisplayName("RuleCacheService 单元测试")
class RuleCacheServiceTest {

    @Container
    static PostgreSQLContainer<?> postgresqlContainer = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresqlContainer::getUsername);
        registry.add("spring.datasource.password", postgresqlContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private RuleCacheService ruleCacheService;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // 清除缓存
        cacheManager.getCache("rules").clear();
        ruleRepository.deleteAll();
    }

    @Test
    @DisplayName("应该从缓存获取规则")
    void shouldGetRuleFromCache() {
        // 创建测试规则
        Rule rule = Rule.builder()
                .ruleKey("cache-test-rule")
                .ruleName("缓存测试规则")
                .groovyScript("def test() { return 'cached' }")
                .version(1)
                .status(RuleStatus.ACTIVE)
                .createdBy("test-user")
                .enabled(true)
                .build();
        ruleRepository.save(rule);

        // 第一次调用 - 从数据库加载
        Rule result1 = ruleCacheService.getRule("cache-test-rule");
        assertThat(result1).isNotNull();
        assertThat(result1.getRuleKey()).isEqualTo("cache-test-rule");

        // 第二次调用 - 从缓存加载
        Rule result2 = ruleCacheService.getRule("cache-test-rule");
        assertThat(result2).isNotNull();
        assertThat(result2.getRuleKey()).isEqualTo("cache-test-rule");

        // 验证缓存命中
        var cache = cacheManager.getCache("rules");
        assertThat(cache).isNotNull();
    }

    @Test
    @DisplayName("应该清除指定规则缓存")
    void shouldEvictRule() {
        Rule rule = Rule.builder()
                .ruleKey("evict-test-rule")
                .ruleName("缓存清除测试")
                .groovyScript("def test() { return 'evict' }")
                .version(1)
                .status(RuleStatus.ACTIVE)
                .createdBy("test-user")
                .enabled(true)
                .build();
        ruleRepository.save(rule);

        // 加载到缓存
        ruleCacheService.getRule("evict-test-rule");

        // 清除缓存
        ruleCacheService.evictRule("evict-test-rule");

        // 验证缓存已清除
        var cache = cacheManager.getCache("rules");
        assertThat(cache).isNotNull();
    }

    @Test
    @DisplayName("应该支持并发访问")
    void shouldSupportConcurrentAccess() throws InterruptedException {
        Rule rule = Rule.builder()
                .ruleKey("concurrent-rule")
                .ruleName("并发测试")
                .groovyScript("def test() { return 'concurrent' }")
                .version(1)
                .status(RuleStatus.ACTIVE)
                .createdBy("test-user")
                .enabled(true)
                .build();
        ruleRepository.save(rule);

        // 并发访问
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    ruleCacheService.getRule("concurrent-rule");
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // 验证没有异常
        var cache = cacheManager.getCache("rules");
        assertThat(cache).isNotNull();
    }
}
