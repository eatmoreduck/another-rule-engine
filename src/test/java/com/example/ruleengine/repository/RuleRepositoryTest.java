package com.example.ruleengine.repository;

import com.example.ruleengine.constants.RuleStatus;
import com.example.ruleengine.domain.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RuleRepository 单元测试
 * 使用 Testcontainers PostgreSQL 进行集成测试
 */
@DataJpaTest
@Testcontainers
@DisplayName("RuleRepository 单元测试")
class RuleRepositoryTest {

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
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RuleRepository ruleRepository;

    private Rule testRule;

    @BeforeEach
    void setUp() {
        testRule = Rule.builder()
                .ruleKey("test-rule-1")
                .ruleName("测试规则1")
                .ruleDescription("这是一个测试规则")
                .groovyScript("def execute() { return 'Hello World' }")
                .version(1)
                .status(RuleStatus.DRAFT)
                .createdBy("test-user")
                .enabled(true)
                .build();

        entityManager.persist(testRule);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("应该成功通过 ruleKey 查询规则")
    void shouldFindByRuleKey() {
        Optional<Rule> found = ruleRepository.findByRuleKey("test-rule-1");

        assertThat(found).isPresent();
        assertThat(found.get().getRuleKey()).isEqualTo("test-rule-1");
        assertThat(found.get().getRuleName()).isEqualTo("测试规则1");
    }

    @Test
    @DisplayName("应该成功查询启用的规则")
    void shouldFindByRuleKeyAndEnabledTrue() {
        Optional<Rule> found = ruleRepository.findByRuleKeyAndEnabledTrue("test-rule-1");

        assertThat(found).isPresent();
        assertThat(found.get().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("查询不存在的规则应该返回空")
    void shouldReturnEmptyForNonExistentRule() {
        Optional<Rule> found = ruleRepository.findByRuleKey("non-existent");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("应该检查 ruleKey 是否存在")
    void shouldCheckRuleKeyExists() {
        boolean exists = ruleRepository.existsByRuleKey("test-rule-1");
        boolean notExists = ruleRepository.existsByRuleKey("non-existent");

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("应该根据状态查询规则")
    void shouldFindByStatus() {
        List<Rule> rules = ruleRepository.findByStatus(RuleStatus.DRAFT);

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getStatus()).isEqualTo(RuleStatus.DRAFT);
    }

    @Test
    @DisplayName("应该查询所有启用的规则")
    void shouldFindByEnabledTrue() {
        List<Rule> rules = ruleRepository.findByEnabledTrue();

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getEnabled()).isTrue();
    }

    @Test
    @DisplayName("应该创建并保存新规则")
    void shouldCreateNewRule() {
        Rule newRule = Rule.builder()
                .ruleKey("new-rule")
                .ruleName("新规则")
                .groovyScript("def execute() { return 'New' }")
                .status(RuleStatus.DRAFT)
                .createdBy("test-user")
                .enabled(true)
                .build();

        Rule saved = ruleRepository.save(newRule);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRuleKey()).isEqualTo("new-rule");
    }

    @Test
    @DisplayName("应该更新规则")
    void shouldUpdateRule() {
        Optional<Rule> found = ruleRepository.findByRuleKey("test-rule-1");
        assertThat(found).isPresent();

        Rule rule = found.get();
        rule.setRuleName("更新后的规则名");
        rule.setVersion(2);

        Rule updated = ruleRepository.save(rule);

        assertThat(updated.getRuleName()).isEqualTo("更新后的规则名");
        assertThat(updated.getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该删除规则")
    void shouldDeleteRule() {
        ruleRepository.delete(testRule);

        Optional<Rule> found = ruleRepository.findByRuleKey("test-rule-1");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("应该验证 ruleKey 唯一约束")
    void shouldEnforceUniqueConstraint() {
        Rule duplicateRule = Rule.builder()
                .ruleKey("test-rule-1") // 重复的 ruleKey
                .ruleName("重复规则")
                .groovyScript("def execute() { return 'Duplicate' }")
                .status(RuleStatus.DRAFT)
                .createdBy("test-user")
                .enabled(true)
                .build();

        // 尝试保存重复的 ruleKey，应该抛出异常
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> {
                    entityManager.persist(duplicateRule);
                    entityManager.flush();
                }
        );
    }

    @Test
    @DisplayName("应该根据状态和启用状态查询规则")
    void shouldFindByStatusAndEnabledTrue() {
        List<Rule> rules = ruleRepository.findByStatusAndEnabledTrue(RuleStatus.DRAFT);

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getStatus()).isEqualTo(RuleStatus.DRAFT);
        assertThat(rules.get(0).getEnabled()).isTrue();
    }
}
