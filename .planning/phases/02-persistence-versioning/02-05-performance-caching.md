# Plan 02-05: 性能优化与缓存

**Wave:** 3 (性能优化)
**Estimated Time:** 4-5 hours
**Status:** Blocked by 02-01, 02-04

## 目标

优化规则加载和执行性能，引入多级缓存策略，确保在持久化层加入后仍能满足 50ms 性能目标。

## 需求映射

- **PERF-01**: 系统使用脚本缓存机制提升规则执行性能
- **PERF-02**: 系统使用特征预加载和批量获取优化性能
- **核心价值**: 50ms 内返回决策结果

## 前置依赖

- ✅ 02-01 完成（数据库基础设施）
- ✅ 02-04 完成（规则生命周期管理）

## 任务列表

### Task 1: 分析当前性能瓶颈
**File:** `src/test/java/com/example/ruleengine/performance/PerformanceBenchmark.java`

创建性能基准测试：
```java
@Test
public void benchmarkRuleLoading() {
    // 测试从数据库加载规则的性能
    // 测试不同数据量下的加载时间
}

@Test
public void benchmarkRuleExecution() {
    // 测试规则执行性能（包含数据库加载）
    // 测试不同复杂度脚本的执行时间
}
```

### Task 2: 设计多级缓存策略
**File:** `src/main/java/com/example/ruleengine/config/CacheConfiguration.java`

设计三级缓存架构：
```
L1: Caffeine (本地缓存)
  ├─ 缓存编译后的 Groovy 脚本
  ├─ 容量: 1000 条规则
  └─ 过期: 5 分钟

L2: Caffeine (本地缓存)
  ├─ 缓存规则元数据（Rule 实体）
  ├─ 容量: 5000 条规则
  └─ 过期: 10 分钟

L3: Redis (可选，分布式缓存)
  ├─ 缓存规则元数据
  ├─ 容量: 无限制
  └─ 过期: 30 分钟
```

### Task 3: 实现 RuleCacheService
**File:** `src/main/java/com/example/ruleengine/cache/RuleCacheService.java`

实现规则缓存服务：
```java
@Service
public class RuleCacheService {
    @Autowired
    private RuleRepository ruleRepository;

    @Cacheable(value = "rules", key = "#ruleKey")
    public Rule getRule(String ruleKey) {
        return ruleRepository.findByRuleKey(ruleKey);
    }

    @CacheEvict(value = "rules", key = "#ruleKey")
    public void evictRule(String ruleKey) {
        // 规则更新时清除缓存
    }

    @CacheEvict(value = "rules", allEntries = true)
    public void evictAll() {
        // 批量清除缓存
    }
}
```

### Task 4: 集成 Spring Cache
**File:** `src/main/resources/application.yml`

配置 Spring Cache：
```yaml
spring:
  cache:
    type: caffeine
    cache-names: rules, compiled-scripts
    caffeine:
      spec: maximumSize=5000,expireAfterWrite=10m
```

### Task 5: 优化 RuleExecutionService
**File:** `src/main/java/com/example/ruleengine/service/RuleExecutionService.java`

集成缓存优化规则加载：
```java
@Service
public class RuleExecutionService {
    @Autowired
    private RuleCacheService ruleCacheService;

    @Autowired
    private GroovyScriptEngine scriptEngine;

    public DecisionResponse decide(DecisionRequest request) {
        // 1. 从缓存加载规则（L1/L2）
        Rule rule = ruleCacheService.getRule(request.getRuleKey());

        // 2. 检查规则状态
        if (rule == null || !rule.getEnabled()) {
            throw new RuleNotFoundException("规则不存在或未启用");
        }

        // 3. 获取编译后的脚本（从 L1 缓存）
        Script script = scriptEngine.getCompiledScript(rule.getGroovyScript());

        // 4. 执行脚本
        // ...
    }
}
```

### Task 6: 实现 Cache Warmer
**File:** `src/main/java/com/example/ruleengine/cache/CacheWarmer.java`

实现缓存预热：
```java
@Component
public class CacheWarmer {
    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private RuleCacheService ruleCacheService;

    @Scheduled(fixedRate = 300000) // 每 5 分钟
    public void warmUpCache() {
        // 预加载所有 ACTIVE 状态的规则到缓存
        List<Rule> activeRules = ruleRepository.findByStatus(RuleStatus.ACTIVE);
        activeRules.forEach(rule -> {
            ruleCacheService.getRule(rule.getRuleKey());
        });
    }

    @PostConstruct
    public void init() {
        // 应用启动时预热缓存
        warmUpCache();
    }
}
```

### Task 7: 添加缓存监控指标
**File:** `src/main/java/com/example/ruleengine/metrics/CacheMetrics.java`

实现缓存监控：
```java
@Component
public class CacheMetrics {
    @Autowired
    private CacheManager cacheManager;

    public Map<String, Object> getCacheStats() {
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache("rules");
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
            (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();

        CacheStats stats = nativeCache.stats();

        return Map.of(
            "hitRate", stats.hitRate(),
            "hitCount", stats.hitCount(),
            "missCount", stats.missCount(),
            "evictionCount", stats.evictionCount(),
            "size", nativeCache.estimatedSize()
        );
    }
}
```

### Task 8: 添加缓存监控端点
**File:** `src/main/java/com/example/ruleengine/controller/CacheController.java`

创建缓存管理 API：
- `GET /api/v1/cache/stats` - 获取缓存统计
- `POST /api/v1/cache/evict/{ruleKey}` - 清除指定规则缓存
- `POST /api/v1/cache/evict-all` - 清除所有缓存
- `POST /api/v1/cache/warm-up` - 手动触发缓存预热

### Task 9: 数据库查询优化
**File:** `src/main/java/com/example/ruleengine/repository/RuleRepository.java`

优化数据库查询：
```java
public interface RuleRepository extends JpaRepository<Rule, Long> {
    // 使用@EntityGraph避免 N+1 查询
    @EntityGraph(attributePaths = {"versions"})
    @Query("SELECT r FROM Rule r WHERE r.ruleKey = :ruleKey")
    Rule findByRuleKeyWithVersions(@Param("ruleKey") String ruleKey);

    // 批量加载规则
    @Query("SELECT r FROM Rule r WHERE r.ruleKey IN :ruleKeys")
    List<Rule> findByRuleKeyIn(@Param("ruleKeys") List<String> ruleKeys);

    // 只查询必要字段（投影查询）
    @Query("SELECT new com.example.ruleengine.projection.RuleProjection(r.ruleKey, r.groovyScript) FROM Rule r WHERE r.ruleKey = :ruleKey")
    RuleProjection findScriptByKey(@Param("ruleKey") String ruleKey);
}
```

### Task 10: 性能测试
**File:** `src/test/java/com/example/ruleengine/performance/RuleExecutionPerformanceTest.java`

创建性能测试：
```java
@Test
public void testDecisionLatencyWithCache() {
    // 测试缓存命中时的决策延迟
    // 目标: P95 < 50ms, P99 < 100ms
}

@Test
public void testCacheHitRate() {
    // 测试缓存命中率
    // 目标: 命中率 > 95%
}

@Test
public void testConcurrentExecution() {
    // 测试并发执行性能
    // 模拟 1000 并发请求
}
```

### Task 11: JPA 性能优化配置
**File:** `src/main/resources/application.yml`

优化 JPA 配置：
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
          fetch_size: 50
        order_inserts: true
        order_updates: true
        batch_versioned_data: true
        cache:
          use_second_level_cache: false # 暂不启用，使用应用层缓存
          use_query_cache: false
```

### Task 12: 单元测试
**File:** `src/test/java/com/example/ruleengine/cache/RuleCacheServiceTest.java`

测试覆盖：
- 缓存命中和未命中
- 缓存过期
- 缓存清除
- 并发访问

## 验证策略

### 性能测试
```bash
# 运行性能基准测试
./gradlew test --tests PerformanceBenchmark

# 运行并发测试
./gradlew test --tests RuleExecutionPerformanceTest

# 使用 JMeter 进行压力测试
jmeter -n -t rule_engine_test.jmx -l result.jtl
```

### 缓存验证
1. 启动应用，触发缓存预热
2. 调用决策 API 多次，验证缓存命中
3. 查看缓存统计，验证命中率 > 95%
4. 更新规则，验证缓存正确清除

### 性能指标
- **规则加载延迟** (从数据库): P95 < 10ms
- **规则执行延迟** (包含加载): P95 < 50ms
- **缓存命中率**: > 95%
- **缓存大小**: < 100MB

## 成功标准

- [ ] 多级缓存策略设计完成
- [ ] RuleCacheService 实现并集成
- [ ] 缓存预热功能正常
- [ ] 缓存监控端点可用
- [ ] 数据库查询优化完成
- [ ] JPA 性能配置优化
- [ ] 性能测试通过（P95 < 50ms）
- [ ] 缓存命中率 > 95%
- [ ] 单元测试通过

## 输出产物

- `PerformanceBenchmark.java` - 性能基准测试
- `CacheConfiguration.java` - 缓存配置
- `RuleCacheService.java` - 规则缓存服务
- `CacheWarmer.java` - 缓存预热
- `CacheMetrics.java` - 缓存监控
- `CacheController.java` - 缓存管理 API
- `RuleRepository.java` (优化) - 数据库查询优化
- `application.yml` (更新) - JPA 和缓存配置
- `RuleExecutionPerformanceTest.java` - 性能测试
- `RuleCacheServiceTest.java` - 单元测试

## 关键设计决策

### 缓存层级选择
- **L1 (Caffeine)**: 编译后的脚本，避免重复编译
- **L2 (Caffeine)**: 规则元数据，减少数据库查询
- **L3 (Redis)**: 可选，用于分布式部署场景

### 缓存更新策略
- **Write-Through**: 更新规则时同步更新缓存
- **Cache-Aside**: 查询时先查缓存，未命中再查数据库
- **选择**: Cache-Aside，简单且高效

### 缓存过期策略
- **Time-Based**: 固定时间过期（5-10 分钟）
- **Event-Based**: 规则更新时主动清除
- **选择**: 两者结合，确保数据一致性

### 缓存粒度
- **粗粒度**: 缓存整个 Rule 实体
- **细粒度**: 缓存 Groovy 脚本字符串
- **选择**: 两者结合，Rule 实体用于元数据查询，脚本用于执行

## 性能优化技巧

### 数据库层面
1. **索引优化**: 为 `rule_key`, `status`, `enabled` 添加索引
2. **查询优化**: 使用投影查询，只查询必要字段
3. **批量操作**: 使用 JDBC batch 插入/更新
4. **连接池**: 配置 HikariCP 连接池（Spring Boot 默认）

### 应用层面
1. **脚本编译缓存**: Phase 1 已实现，避免重复编译
2. **规则元数据缓存**: 本计划实现
3. **特征预加载**: Phase 1 已实现
4. **并发优化**: 使用虚拟线程（Java 21）或线程池

### JVM 层面
1. **JVM 参数**: 调整堆内存、GC 参数
2. **GC 调优**: 选择合适的 GC 器（G1GC、ZGC）
3. **类加载**: Groovy 类加载隔离（Phase 1 已实现）

## 风险与注意事项

1. **缓存一致性问题**: 规则更新后缓存可能未及时清除
   - **解决**: 使用 `@CacheEvict` 注解自动清除缓存

2. **缓存穿透**: 恶意查询不存在的规则
   - **解决**: 缓存 null 值，使用布隆过滤器

3. **缓存雪崩**: 大量缓存同时过期
   - **解决**: 添加随机过期时间

4. **内存溢出**: 缓存数据过多
   - **解决**: 设置最大容量，使用 LRU 淘汰策略

5. **分布式缓存一致**: 多实例部署时缓存不一致
   - **解决**: 使用 Redis 发布/订阅机制同步缓存更新

## 后续优化方向

1. **Redis 集成**: 分布式部署时引入 Redis
2. **本地缓存一致性**: 使用 Redis Pub/Sub 同步多实例缓存
3. **智能预加载**: 根据规则使用频率预加载热点规则
4. **编译结果持久化**: 将编译后的脚本序列化存储

## 相关文档

- Caffeine: https://github.com/ben-manes/caffeine
- Spring Cache: https://spring.io/guides/gs/caching/
- HikariCP: https://github.com/brettwooldridge/HikariCP
