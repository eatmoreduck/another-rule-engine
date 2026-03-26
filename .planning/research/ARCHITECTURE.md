# 架构模式

**领域：** 风控规则引擎
**研究时间：** 2025-03-26
**整体信心：** HIGH

## 推荐架构

基于对2025年风控规则引擎系统的深入研究，针对电商反欺诈场景的低代码规则引擎，推荐采用**五层分层架构**，结合**微服务设计原则**和**前后端分离模式**。

```
┌─────────────────────────────────────────────────────────────┐
│                        前端层 (Frontend)                      │
│         Vue3 + Element Plus + AntV G6 (可视化流程编排)         │
└─────────────────────────────────────────────────────────────┘
                              │ HTTP/REST
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        接入层 (API Gateway)                   │
│  风控网关：鉴权、参数校验、幂等性、任务派发、记录落地、预警      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      核心引擎层 (Core Engine)                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ 规则解析器│  │流程编排器│  │版本管理器│  │灰度控制器│    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      执行层 (Execution Layer)                  │
│  ┌────────────────┐         ┌────────────────┐              │
│  │  同步执行器     │         │  异步执行器     │              │
│  │  (GroovyShell) │         │  (事件驱动)     │              │
│  └────────────────┘         └────────────────┘              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      存储层 (Storage Layer)                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │PostgreSQL│  │  Redis   │  │ MongoDB  │  │  Groovy  │    │
│  │(规则元数据)│  │(运行时缓存)│  │(审核记录)│  │(DSL存储) │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      监控层 (Monitoring Layer)                 │
│  Prometheus + Grafana + ELK (执行日志、性能指标、异常告警)     │
└─────────────────────────────────────────────────────────────┘
```

## 组件边界

| 组件 | 职责 | 通信对象 |
|------|------|----------|
| **前端层** | 提供规则可视化配置界面、表单配置、审核记录查询、报表展示 | 接入层 (HTTP/REST) |
| **风控网关** | 统一入口、鉴权、参数校验、幂等性校验、任务派发、审核记录落地 | 核心引擎层、存储层 |
| **规则解析器** | 解析Groovy DSL、编译规则、表达式求值 | 执行层、Groovy存储 |
| **流程编排器** | 规则集编排、执行顺序控制、条件分支处理 | 执行层、版本管理器 |
| **版本管理器** | 规则版本存储、版本对比、回滚、灰度发布控制 | PostgreSQL、灰度控制器 |
| **灰度控制器** | 流量分流、灰度策略执行、版本路由 | 核心引擎层、Redis |
| **同步执行器** | Groovy脚本同步执行、结果返回 | 规则解析器、特征平台 |
| **异步执行器** | 事件驱动、消息队列、回调处理 | 规则解析器、MQ |
| **特征服务** | 特征获取、入参优先、降级策略 | 外部特征平台 |
| **监控服务** | 性能监控、异常告警、数据统计 | 所有层 |

### 核心组件详细说明

#### 1. 风控网关 (Risk Control Gateway)
**唯一对外入口**，不处理具体审核逻辑，只做：
- 请求鉴权与权限校验
- 参数合法性校验
- 幂等性校验 (基于requestId)
- 审核任务派发到引擎层
- 审核记录落地 (异步)
- 风险预警触发

**关键设计决策：**
- 使用 Spring Boot @RestController 实现
- 支持同步和异步两种执行模式
- 超时熔断保护 (防止影响业务系统)

#### 2. 规则解析器 (Rule Parser)
负责 Groovy DSL 的解析和执行：
- 将规则 DSL 编译为可执行的 Groovy 脚本
- 管理脚本缓存 (防止频繁类加载)
- 表达式求值 (支持复杂逻辑)
- 沙箱安全隔离

**关键设计决策：**
- 使用 GroovyShell + GroovyClassLoader
- 基于 MD5(script) 的缓存机制
- ThreadLocal 隔离执行上下文

#### 3. 流程编排器 (Workflow Orchestrator)
管理规则执行的流程控制：
- 规则集编排 (最坏匹配、权重匹配、自定义匹配)
- 执行顺序控制 (优先级、命中即终止)
- 条件分支处理 (AND/OR 逻辑)
- 状态机管理 (PENDING → RUNNING → COMPLETED/FAILED)

**关键设计决策：**
- 使用 Spring State Machine 管理状态
- 责任链模式处理规则执行
- 支持规则间的依赖关系

#### 4. 版本管理器 (Version Manager)
规则的版本生命周期管理：
- 多版本存储 (PostgreSQL JSONB)
- 版本对比与差异分析
- 一键回滚到历史版本
- 版本状态管理 (草稿、已发布、已归档)

**关键设计决策：**
- 每次规则变更创建新版本
- 保留所有历史版本
- Git-like 的版本管理理念

#### 5. 灰度控制器 (Canary Controller)
实现规则的灰度发布：
- 按比例流量分流 (5% → 50% → 100%)
- 按用户标签定向灰度
- A/B 测试支持
- 自动回滚机制 (异常率超阈值)

**关键设计决策：**
- 基于 Redis 的流量计数器
- 支持实时灰度比例调整
- 监控指标驱动自动回滚

## 数据流

### 同步执行流程

```
┌─────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│ 业务系统 │ ──▶ │ 风控网关  │ ──▶ │流程编排器│ ──▶ │规则解析器│ ──▶ │同步执行器 │
└─────────┘     └──────────┘     └──────────┘     └──────────┘     └──────────┘
                    │                  │                  │                  │
                    ▼                  ▼                  ▼                  ▼
              幂等性校验         版本选择         Groovy编译         规则执行
              参数校验          灰度路由         缓存查找            特征获取
                    │                  │                  │                  │
                    └──────────────────┴──────────────────┴──────────────────┘
                                                          │
                                                          ▼
                                                  ┌──────────────┐
                                                  │ PostgreSQL   │
                                                  │ (审核记录)    │
                                                  └──────────────┘
                                                          │
                                                          ▼
                                                  ┌──────────────┐
                                                  │   返回结果   │◀──────┘
                                                  │ (风险等级)   │
                                                  └──────────────┘
```

### 异步执行流程

```
┌─────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│ 业务系统 │ ──▶ │ 风控网关  │ ──▶ │ MQ Topic │ ──▶ │异步执行器 │
└─────────┘     └──────────┘     └──────────┘     └──────────┘
                    │                                  │
                    ▼                                  ▼
              返回 202 Accepted                     规则执行
              (taskId)                              特征获取
                                                       │
                                                       ▼
                                                ┌──────────────┐
                                                │ Redis        │
                                                │ (任务状态)    │
                                                └──────────────┘
                                                       │
                       ┌───────────────────────────────┘
                       │
                       ▼
                ┌──────────────┐
                │ 回调通知      │
                │ (Webhook)    │
                └──────────────┘
```

### 灰度发布流程

```
                    ┌──────────────┐
                    │  流量请求     │
                    └──────────────┘
                            │
                            ▼
                    ┌──────────────┐
                    │ 灰度控制器   │
                    └──────────────┘
                            │
              ┌─────────────┼─────────────┐
              │             │             │
              ▼             ▼             ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │  5% 流量  │  │  50% 流量 │  │ 100% 流量 │
        │  新版本   │  │  新版本   │  │  新版本   │
        └──────────┘  └──────────┘  └──────────┘
              │             │             │
              │             │             │
              └─────────────┴─────────────┘
                            │
                            ▼
                    ┌──────────────┐
                    │ 监控指标收集  │
                    │ (异常率、延迟)│
                    └──────────────┘
                            │
              ┌─────────────┴─────────────┐
              │                           │
              ▼ 异常率 > 5%              ▼ 正常
        ┌──────────────┐           ┌──────────────┐
        │ 自动回滚      │           │ 继续灰度     │
        │ 到上一版本    │           │ 扩大流量     │
        └──────────────┘           └──────────────┘
```

## 模式应用

### 模式 1: 责任链模式 (Chain of Responsibility)
**用途：** 规则执行链路处理
**场景：** 多个规则按优先级依次执行，每个规则独立判断是否命中

**示例实现：**
```java
public abstract class RuleHandler {
    private RuleHandler next;

    public void setNext(RuleHandler next) {
        this.next = next;
    }

    public void handle(RuleContext context) {
        if (canHandle(context)) {
            process(context);
        }
        if (next != null) {
            next.handle(context);
        }
    }

    protected abstract boolean canHandle(RuleContext context);
    protected abstract void process(RuleContext context);
}

// 同步规则处理器
@Component
public class SyncRuleHandler extends RuleHandler {
    @Override
    protected boolean canHandle(RuleContext context) {
        return !context.isAsync();
    }

    @Override
    protected void process(RuleContext context) {
        // 执行同步规则逻辑
        executeRule(context);
    }
}
```

**何时使用：**
- 规则间有明确的执行顺序
- 需要支持"命中即终止"逻辑
- 规则可动态组合

### 模式 2: 模板方法模式 (Template Method)
**用途：** 定义规则执行的标准流程骨架
**场景：** 所有规则执行都遵循相同的步骤：预处理 → 执行 → 后处理

**示例实现：**
```java
public abstract class RuleTemplate {
    public final RuleResult execute(RuleContext context) {
        // 前置处理
        preProcess(context);

        // 执行规则
        RuleResult result = doExecute(context);

        // 后置处理
        postProcess(context, result);

        return result;
    }

    protected void preProcess(RuleContext context) {
        // 默认前置处理：参数校验、特征准备
        validateParams(context);
        prepareFeatures(context);
    }

    protected abstract RuleResult doExecute(RuleContext context);

    protected void postProcess(RuleContext context, RuleResult result) {
        // 默认后置处理：记录日志、更新缓存
        logExecution(context, result);
        updateCache(context, result);
    }
}
```

**何时使用：**
- 规则执行流程固定，但具体实现不同
- 需要统一的异常处理和日志记录
- 避免代码重复

### 模式 3: 策略模式 (Strategy)
**用途：** 不同规则类型的执行策略
**场景：** 统计规则、条件规则、自定义规则各有不同执行逻辑

**示例实现：**
```java
public interface RuleExecutionStrategy {
    RuleResult execute(RuleDefinition rule, RuleContext context);
}

// 统计规则策略
@Component
public class StatisticalRuleStrategy implements RuleExecutionStrategy {
    @Override
    public RuleResult execute(RuleDefinition rule, RuleContext context) {
        // 统计类规则逻辑：计数、求和、去重
        return evaluateStatistical(rule, context);
    }
}

// 条件规则策略
@Component
public class ConditionalRuleStrategy implements RuleExecutionStrategy {
    @Override
    public RuleResult execute(RuleDefinition rule, RuleContext context) {
        // 条件类规则逻辑：表达式求值
        return evaluateCondition(rule, context);
    }
}

// 策略工厂
@Service
public class RuleStrategyFactory {
    private Map<RuleType, RuleExecutionStrategy> strategies;

    public RuleExecutionStrategy getStrategy(RuleType type) {
        return strategies.get(type);
    }
}
```

**何时使用：**
- 有多种规则类型，每种类型执行逻辑不同
- 需要动态选择执行策略
- 规则类型可能扩展

### 模式 4: 观察者模式 (Observer)
**用途：** 规则执行结果通知
**场景：** 规则命中后需要触发多种后续操作：记录日志、发送告警、更新统计

**示例实现：**
```java
public interface RuleExecutionListener {
    void onRuleHit(RuleHitEvent event);
    void onRuleMiss(RuleMissEvent event);
}

// 日志监听器
@Component
public class LoggingListener implements RuleExecutionListener {
    @Override
    public void onRuleHit(RuleHitEvent event) {
        log.info("Rule hit: {}", event.getRuleId());
    }
}

// 告警监听器
@Component
public class AlertListener implements RuleExecutionListener {
    @Override
    public void onRuleHit(RuleHitEvent event) {
        if (event.getRiskLevel() >= RISK_THRESHOLD) {
            sendAlert(event);
        }
    }
}

// 规则执行器
@Service
public class RuleExecutor {
    private List<RuleExecutionListener> listeners;

    public RuleResult execute(RuleDefinition rule, RuleContext context) {
        RuleResult result = doExecute(rule, context);

        if (result.isHit()) {
            RuleHitEvent event = new RuleHitEvent(rule, context, result);
            listeners.forEach(l -> l.onRuleHit(event));
        }

        return result;
    }
}
```

**何时使用：**
- 规则执行后需要触发多个副作用
- 需要松耦合的事件通知机制
- 监听器可动态增减

### 模式 5: 工厂模式 (Factory)
**用途：** 规则实例创建
**场景：** 根据规则配置创建对应的规则对象

**示例实现：**
```java
@Service
public class RuleFactory {
    public Rule createRule(RuleDefinition definition) {
        switch (definition.getType()) {
            case STATISTICAL:
                return new StatisticalRule(definition);
            case CONDITIONAL:
                return new ConditionalRule(definition);
            case COMPOSITE:
                return new CompositeRule(definition);
            default:
                throw new UnsupportedRuleTypeException(definition.getType());
        }
    }
}
```

**何时使用：**
- 规则对象创建逻辑复杂
- 需要统一创建入口
- 规则类型可能扩展

## 反模式避免

### 反模式 1: God Object (上帝对象)
**问题：** 一个类承担过多职责
**为什么不好：**
- 代码难以维护和测试
- 违反单一职责原则
- 修改风险高

**不要这样做：**
```java
// ❌ 反模式：一个类做所有事情
@Service
public class RuleEngineService {
    // 规则解析
    public Rule parseRule(String dsl) { ... }

    // 规则执行
    public RuleResult execute(Rule rule, Context ctx) { ... }

    // 版本管理
    public void saveVersion(Rule rule) { ... }

    // 灰度控制
    public boolean canaryTest(Rule rule, int ratio) { ... }

    // 监控告警
    public void sendAlert(RuleResult result) { ... }
}
```

**应该这样做：**
```java
// ✅ 正确：职责分离
@Service
public class RuleParser {
    public Rule parseRule(String dsl) { ... }
}

@Service
public class RuleExecutor {
    public RuleResult execute(Rule rule, Context ctx) { ... }
}

@Service
public class VersionManager {
    public void saveVersion(Rule rule) { ... }
}

@Service
public class CanaryController {
    public boolean canaryTest(Rule rule, int ratio) { ... }
}

@Service
public class AlertService {
    public void sendAlert(RuleResult result) { ... }
}
```

### 反模式 2: 硬编码规则 (Hard-coded Rules)
**问题：** 规则逻辑硬编码在代码中
**为什么不好：**
- 无法动态调整规则
- 每次修改需要重新发布
- 违背低代码设计目标

**不要这样做：**
```java
// ❌ 反模式：规则硬编码
public class FraudDetector {
    public boolean detect(Transaction tx) {
        // 硬编码规则：金额 > 10000 且 异地交易
        if (tx.getAmount() > 10000 &&
            !tx.getProvince().equals(tx.getUserProvince())) {
            return true;
        }
        return false;
    }
}
```

**应该这样做：**
```java
// ✅ 正确：规则配置化
@Service
public class RuleEngineService {
    public RuleResult execute(String ruleId, Context context) {
        Rule rule = ruleRepository.findById(ruleId);
        return ruleExecutor.execute(rule, context);
    }
}

// 规则存储在数据库中，可通过 UI 配置
// Rule {
//   id: "rule_001"
//   expression: "amount > 10000 && province != userProvince"
// }
```

### 反模式 3: 缺乏缓存机制 (No Caching)
**问题：** 频繁编译和加载 Groovy 脚本
**为什么不好：**
- 性能差，无法满足 50ms 延迟要求
- 内存占用高（频繁类加载）
- CPU 资源浪费

**不要这样做：**
```java
// ❌ 反模式：每次都编译
public class RuleExecutor {
    public RuleResult execute(String script, Context context) {
        // 每次都编译，性能极差
        GroovyShell shell = new GroovyShell();
        Script compiled = shell.parse(script);
        return compiled.run();
    }
}
```

**应该这样做：**
```java
// ✅ 正确：缓存编译结果
@Component
public class GroovyScriptManager {
    private final ConcurrentMap<String, Script> cache = new ConcurrentHashMap<>();

    public Script getScript(String script) {
        return cache.computeIfAbsent(script, s -> {
            GroovyShell shell = new GroovyShell();
            return shell.parse(s);
        });
    }
}

@Service
public class RuleExecutor {
    @Autowired
    private GroovyScriptManager scriptManager;

    public RuleResult execute(String script, Context context) {
        Script compiled = scriptManager.getScript(script);
        return compiled.run();
    }
}
```

### 反模式 4: 同步阻塞外部调用
**问题：** 在规则执行过程中同步调用外部服务
**为什么不好：**
- 外部服务延迟会影响整体性能
- 外部服务故障会导致规则执行失败
- 无法满足 50ms 延迟要求

**不要这样做：**
```java
// ❌ 反模式：同步调用外部特征平台
public class RuleExecutor {
    public RuleResult execute(Rule rule, Context context) {
        // 同步调用外部特征平台，延迟不可控
        Feature feature = featureService.getFeature(context.getUserId());
        context.addFeature(feature);

        return doExecute(rule, context);
    }
}
```

**应该这样做：**
```java
// ✅ 正确：超时熔断 + 降级策略
@Service
public class FeatureService {
    @HystrixCommand(
        fallbackMethod = "getDefaultFeature",
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "50")
        }
    )
    public Feature getFeature(String userId) {
        // 调用外部特征平台
        return externalFeatureClient.getFeature(userId);
    }

    public Feature getDefaultFeature(String userId) {
        // 降级策略：返回默认特征值
        return Feature.DEFAULT;
    }
}

// 或者：入参优先，减少外部调用
public class RuleExecutor {
    public RuleResult execute(Rule rule, Context context) {
        // 优先使用入参中的特征
        Feature feature = context.getFeature();
        if (feature == null) {
            // 特征不存在时才调用外部，且设置超时
            feature = featureService.getFeature(context.getUserId());
        }

        return doExecute(rule, context);
    }
}
```

### 反模式 5: 忽略版本管理
**问题：** 规则变更没有版本控制
**为什么不好：**
- 无法回滚到历史版本
- 无法追溯规则变更历史
- 灰度发布无法实施

**不要这样做：**
```java
// ❌ 反模式：直接更新规则，无版本管理
@Service
public class RuleService {
    public void updateRule(String ruleId, Rule newRule) {
        // 直接覆盖，历史版本丢失
        ruleRepository.save(newRule);
    }
}
```

**应该这样做：**
```java
// ✅ 正确：每次更新创建新版本
@Service
public class RuleService {
    public void updateRule(String ruleId, Rule newRule) {
        Rule oldRule = ruleRepository.findById(ruleId);

        // 创建新版本
        RuleVersion newVersion = RuleVersion.builder()
            .ruleId(ruleId)
            .version(oldRule.getVersion() + 1)
            .content(newRule.getContent())
            .createdAt(LocalDateTime.now())
            .createdBy(getCurrentUser())
            .build();

        // 保存新版本
        versionRepository.save(newVersion);

        // 更新当前版本指针
        oldRule.setCurrentVersion(newVersion.getVersion());
        ruleRepository.save(oldRule);
    }

    public void rollback(String ruleId, int targetVersion) {
        Rule rule = ruleRepository.findById(ruleId);
        RuleVersion target = versionRepository.findByRuleIdAndVersion(ruleId, targetVersion);

        // 恢复到目标版本（创建新版本，内容与目标版本相同）
        RuleVersion rollbackVersion = RuleVersion.builder()
            .ruleId(ruleId)
            .version(rule.getCurrentVersion() + 1)
            .content(target.getContent())
            .rollbackFrom(rule.getCurrentVersion())
            .createdAt(LocalDateTime.now())
            .createdBy(getCurrentUser())
            .build();

        versionRepository.save(rollbackVersion);
        rule.setCurrentVersion(rollbackVersion.getVersion());
        ruleRepository.save(rule);
    }
}
```

## 可扩展性考虑

### 规模扩展场景

| 关注点 | 100 用户 | 10K 用户 | 1M 用户 |
|--------|----------|----------|---------|
| **规则执行** | 单机同步执行 | 分布式执行 + Redis 队列 | 分片执行 + MQ |
| **规则存储** | PostgreSQL 单表 | PostgreSQL 分表 | PostgreSQL + MongoDB 混合存储 |
| **缓存策略** | 本地缓存 | Redis 集中式缓存 | Redis 集群 + 多级缓存 |
| **特征获取** | 同步调用 | 异步调用 + 超时降级 | 特征预计算 + 本地缓存 |
| **监控告警** | 日志文件 | ELK 日志中心 | Prometheus + Grafana + 分布式追踪 |
| **灰度发布** | 按比例分流 | 按用户标签分流 | 按地区/设备/行为多维分流 |

### 扩展性设计建议

1. **接口优先设计**
   - 所有核心组件通过接口定义
   - 依赖注入而非硬编码依赖
   - 便于替换实现和扩展功能

2. **插件化架构**
   - 规则执行器支持插件扩展
   - 监听器模式支持自定义事件处理
   - 策略模式支持多种规则类型

3. **水平扩展能力**
   - 无状态服务设计
   - 分布式缓存 (Redis Cluster)
   - 消息队列解耦 (Kafka/RabbitMQ)

4. **垂直扩展优化**
   - Groovy 脚本编译缓存
   - 特征预计算和缓存
   - 连接池和线程池调优

## Sources

**HIGH Confidence (官方文档和权威来源):**
- [Groovy-based Rule Engine Tutorial](https://pleus.net/articles/grules/grules.pdf) - Groovy 规则引擎实现原理
- [Baeldung: Implementing a Simple Rule Engine in Java](https://www.baeldung.com/spring-spel-implement-simple-rule-engine) - Spring 规则引擎实现
- [Spring State Machine Documentation](https://spring.io/projects/spring-statemachine) - 状态机官方文档
- [Spring Boot Application Architecture Patterns](https://github.com/sivaprasadreddy/spring-boot-application-architecture-patterns) - Spring Boot 架构模式

**MEDIUM Confidence (行业实践和社区验证):**
- [手把手教你搭建轻量级电商风控平台-规则引擎篇](https://juejin.cn/post/7490400682313236517) - 电商风控平台实践（掘金 2025-04-07）
- [规则引擎设计实战-从零构建分布式规则引擎系统](https://juejin.cn/post/7478234774046883855) - 分布式规则引擎架构设计（掘金 2025-03-06）
- [Decoupling Decisions: Integrating Business Rules Engine into Microservices](https://www.higson.io/blog/decoupling-decisions-how-to-integrate-business-rules-engine-into-your-microservices-architecture-for-agility) - 微服务架构下的规则引擎集成
- [责任链模式 - Refactoring.Guru](https://refactoringguru.cn/design-patterns/chain-of-responsibility) - 责任链模式权威解释

**LOW Confidence (需要验证):**
- 部分性能数据和优化建议来自个人经验，需要实际压测验证
- 某些架构模式的具体实现细节可能需要根据项目实际情况调整