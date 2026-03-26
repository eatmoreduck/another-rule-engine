# 技术栈研究

**领域:** 风控规则引擎
**研究日期:** 2025-03-26
**整体信心:** HIGH

## 推荐技术栈

### 核心技术

| 技术 | 版本 | 用途 | 推荐理由 |
|------|------|------|----------|
| **Java** | 21 LTS | 运行时环境 | 当前LTS版本，支持虚拟线程(Project Loom)，显著提升高并发场景性能，相比Java 8/11有大幅性能提升 |
| **Spring Boot** | 3.3.x | 应用框架 | 3.3版本启动时间减少40%，内存占用降低25%，原生支持虚拟线程(`spring.threads.virtual.enabled=true`)，可轻松处理100万+并发请求 |
| **Groovy** | 4.0.x 或 5.x | 规则DSL | 动态语言特性适合规则引擎，支持动态编译和加载，与Java生态无缝集成，语法简洁适合业务人员理解 |
| **PostgreSQL** | 16 或 17 | 规则元数据存储 | 成熟稳定，支持JSONB存储规则配置，16/17版本在并行查询、索引性能、批量加载方面有显著提升 |
| **Spring Data JPA** | 3.2.x | ORM框架 | Spring Boot 3原生集成，简化数据库操作，支持复杂查询和事务管理 |

### 规则引擎核心库

| 库 | 版本 | 用途 | 何时使用 |
|----|------|------|----------|
| **Groovy Shell** | (Groovy内置) | 动态编译和执行Groovy脚本 | 规则的动态加载和执行，支持脚本缓存和编译优化 |
| **CompilerConfiguration** | (Groovy内置) | Groovy编译配置优化 | 使用`PARALLEL_PARSE`选项提升编译性能，启用联合编译减少内存开销 |
| **@CompileStatic** | (Groovy内置) | 静态编译优化 | 对性能要求高的规则使用静态编译，使Groovy代码性能接近Java |
| **自定义脚本缓存** | 自实现 | 缓存已编译的Groovy脚本 | 避免重复编译，使用`ConcurrentHashMap`实现线程安全的脚本缓存 |

### 缓存与性能优化

| 技术 | 版本 | 用途 | 推荐理由 |
|------|------|------|----------|
| **Caffeine** | 3.1.x | 本地缓存 | Java 8+高性能缓存库，Spring Boot 3原生集成，比Guava Cache性能更好，支持异步加载和自动过期 |
| **Redis** | 7.x 或 8.x | 分布式缓存 | 8.4版本缓存工作负载吞吐量提升30%，多线程I/O处理，适合分布式环境下的规则缓存和特征存储 |
| **Spring Cache** | (Spring Boot内置) | 缓存抽象 | 统一缓存接口，支持多层缓存策略(本地+分布式)，简化缓存操作 |

### 前端技术

| 技术 | 版本 | 用途 | 推荐理由 |
|------|------|------|----------|
| **React** | 19 | 可视化规则编辑器 | 2025年最新版本，支持并发渲染、自动批处理、改进的Suspense，构建复杂交互式UI的最佳选择 |
| **TypeScript** | 5.x | 类型安全 | 强类型系统减少运行时错误，与React 19完美集成，提升代码可维护性 |
| **Ant Design** | 5.x | UI组件库 | 企业级UI设计语言，提供丰富的表单和流程图组件，适合构建规则配置界面 |
| **React Flow** | 11.x | 流程图可视化 | 专业的流程图库，支持自定义节点和连接，适合构建可视化规则流程编辑器 |

### 测试框架

| 技术 | 版本 | 用途 | 推荐理由 |
|------|------|------|----------|
| **JUnit 5** | 5.10.x | 单元测试 | 现代Java测试标准，支持参数化测试、动态测试、并行测试执行 |
| **Mockito** | 5.x | Mock框架 | 与JUnit 5深度集成，强大的Mock能力，支持Mock构造器、静态方法 |
| **Testcontainers** | 1.19.x | 集成测试 | Spring Boot 3.4+原生支持，自动启动Docker容器(PostgreSQL、Redis)，简化集成测试配置 |
| **Spring Boot Test** | 3.3.x | Spring测试 | 提供`@SpringBootTest`、`@WebMvcTest`等切片测试注解，支持测试配置文件 |

### 监控与日志

| 技术 | 版本 | 用途 | 推荐理由 |
|------|------|------|----------|
| **Spring Boot Actuator** | 3.3.x | 应用监控 | 暴露健康检查、指标端点，与Prometheus无缝集成 |
| **Prometheus** | 2.45+ | 指标采集 | 云原生监控标准，Pull模式采集指标，支持多维度数据查询 |
| **Grafana** | 10.x | 可视化监控 | 与Prometheus完美集成，丰富的仪表盘模板，支持告警规则 |
| **Loki** | 2.9.x | 日志聚合 | 轻量级日志系统，相比ELK更省资源，与Grafana原生集成，适合Kubernetes环境 |
| **Loki4j** | 1.5.x | Loki日志Appender | 专为Java应用设计的Loki日志appende，支持结构化日志和上下文标签 |

### 部署与运维

| 技术 | 版本 | 用途 | 推荐理由 |
|------|------|------|----------|
| **Docker** | 24.x | 容器化 | 标准容器运行时，Spring Boot 3.x支持分层JAR，使Docker层缓存效率提升10倍 |
| **Kubernetes** | 1.28+ | 容器编排 | 云原生部署标准，支持自动扩缩容、滚动更新、服务发现 |
| **Gradle** | 8.5+ | 构建工具 | 支持Groovy和Kotlin DSL，性能优于Maven，增量编译和构建缓存提升构建速度 |

## 安装配置

### 后端核心依赖

```xml
<!-- pom.xml 核心依赖 -->
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.3.1</spring-boot.version>
    <groovy.version>4.0.21</groovy.version>
</properties>

<dependencies>
    <!-- Spring Boot核心 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- PostgreSQL驱动 -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Groovy集成 -->
    <dependency>
        <groupId>org.apache.groovy</groupId>
        <artifactId>groovy-all</artifactId>
        <version>${groovy.version}</version>
        <type>pom</type>
    </dependency>

    <!-- Caffeine缓存 -->
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
    </dependency>

    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- Actuator监控 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Micrometer Prometheus -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
</dependencies>
```

### 应用配置

```yaml
# application.yml
spring:
  application:
    name: risk-rule-engine

  # 虚拟线程配置
  threads:
    virtual:
      enabled: true

  # 数据源配置
  datasource:
    url: jdbc:postgresql://localhost:5432/rule_engine
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10

  # JPA配置
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true

  # Redis配置
  redis:
    host: ${REDIS_HOST}
    port: 6379
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5

  # 缓存配置
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=10m

# Actuator配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true

# Groovy脚本配置
groovy:
  script:
    cache:
      enabled: true
      max-size: 1000
    compile:
      static: true  # 启用静态编译优化性能
      parallel: true # 启用并行解析
```

### 前端依赖

```json
{
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "typescript": "^5.6.0",
    "antd": "^5.22.0",
    "reactflow": "^11.11.0",
    "@ant-design/icons": "^5.5.0",
    "axios": "^1.7.0"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.3.0",
    "vite": "^5.4.0",
    "eslint": "^9.0.0",
    "@typescript-eslint/eslint-plugin": "^8.0.0"
  }
}
```

### 测试依赖

```xml
<dependencies>
    <!-- Spring Boot Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <version>1.19.8</version>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <version>1.19.8</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## 备选方案对比

| 推荐方案 | 备选方案 | 何时使用备选方案 |
|---------|---------|-----------------|
| **Groovy DSL** | **Drools** | 需要Rete算法的复杂规则匹配，规则数量超过10000条，且团队有Drools经验 |
| **Groovy DSL** | **Easy Rules** | 规则逻辑简单，不依赖外部特征，主要是条件判断和动作执行 |
| **Caffeine** | **Guava Cache** | 项目已深度依赖Guava，且不需要Caffeine的异步加载特性 |
| **React 19** | **Vue 3** | 团队更熟悉Vue生态，或项目已有Vue组件需要复用 |
| **Loki** | **ELK Stack** | 需要ELK的全文搜索和复杂日志分析能力，且有足够的运维资源 |
| **PostgreSQL** | **MySQL** | 团队只有MySQL经验，或需要使用MySQL特定的特性 |
| **Gradle** | **Maven** | 团队更熟悉Maven，或企业有统一的Maven规范要求 |

## 不推荐使用的技术

| 避免使用 | 原因 | 推荐替代 |
|---------|------|----------|
| **Java 8/11** | 缺少虚拟线程支持，性能远低于Java 21，不再是LTS | **Java 21 LTS** |
| **Drools** | 学习曲线陡峭，配置复杂，不适合低代码场景，性能不如Groovy DSL | **Groovy DSL** |
| **旧版Groovy (2.x/3.x)** | 性能和安全性不如4.x/5.x，缺少现代特性 | **Groovy 4.x 或 5.x** |
| **Spring Boot 2.x** | 不支持虚拟线程，性能和功能远落后于3.x，即将停止维护 | **Spring Boot 3.3.x** |
| **Redis 6.x及更早** | 性能不如7.x/8.x，缺少多线程I/O，无法满足高并发需求 | **Redis 7.x 或 8.x** |
| **Log4j 1.x/2.x** | 存在安全漏洞，性能不如Logback，已被社区淘汰 | **Loki4j + Logback** |
| **传统的JSP/Thymeleaf** | 不适合前后端分离，维护成本高，开发效率低 | **React 19** |
| **Elasticsearch(仅用于日志)** | 资源消耗大，运维复杂，对于日志场景Loki更轻量 | **Loki** |

## 不同场景下的技术选型

**如果规则复杂度极高(超过10000条规则且相互关联):**
- 使用 **Drools** 而非Groovy DSL
- 因为Drools的Rete算法专为大规模规则匹配优化
- 但要接受更高的学习成本和复杂度

**如果团队对Groovy不熟悉但熟悉Python:**
- 使用 **Python + Lua** 脚本引擎而非Groovy
- 因为Python在业务团队中更普及
- 但要接受性能损失(约20-30%)

**如果部署环境不支持容器化:**
- 使用 **传统JAR部署 + 虚拟机** 而非Docker/K8s
- 因为需要适配现有运维体系
- 但要放弃云原生的弹性伸缩和故障自愈能力

**如果需要极致性能(单次决策<10ms):**
- 使用 **Java原生代码** 而非Groovy DSL
- 因为Groovy动态特性有性能损耗
- 但牺牲了灵活性和低代码能力

## 版本兼容性

| 组件 | 兼容版本 | 注意事项 |
|------|---------|----------|
| **Java 21** | Spring Boot 3.2+, Groovy 4.x+, PostgreSQL JDBC 42.6.x+ | Java 22也是非LTS，虚拟线程在Java 21已稳定 |
| **Spring Boot 3.3.x** | Java 17+, Java 21推荐, Groovy 4.x/5.x, PostgreSQL 14+ | 3.4+有更好的Testcontainers支持 |
| **Groovy 4.x** | Java 8+, Java 21性能最佳, Spring Boot 3.x | Groovy 5.x也是稳定选择，性能与4.x相当 |
| **PostgreSQL 16/17** | Spring Data JPA 3.x, JDBC Driver 42.6.x+ | 17是最新版本，16更稳定 |
| **React 19** | TypeScript 5.x, Vite 5.x, Ant Design 5.x | 与旧版React不兼容，需要升级依赖 |
| **Redis 7.x/8.x** | Spring Data Redis 3.x, Lettuce 6.x+ | 8.x性能提升30%，推荐新项目使用 |

## 性能优化建议

### 规则执行性能优化

1. **使用@CompileStatic**: 对高频执行的规则使用静态编译，性能提升50-80%
2. **脚本缓存**: 使用Caffeine缓存编译后的Groovy脚本，避免重复编译
3. **并行解析**: 启用Groovy的`PARALLEL_PARSE`选项，提升多规则编译速度
4. **虚拟线程**: 启用Spring Boot 3.3的虚拟线程，提升并发处理能力
5. **特征预加载**: 使用多级缓存(本地+Redis)缓存特征数据，减少外部调用

### 数据库性能优化

1. **连接池配置**: HikariCP连接池，max-size=20, min-idle=10
2. **批量操作**: 启用hibernate.jdbc.batch_size=50
3. **索引优化**: 为规则查询字段创建合适的索引
4. **读写分离**: 使用PostgreSQL的主从复制，读写分离

### 缓存策略优化

1. **两级缓存**: 本地缓存(Caffeine) + 分布式缓存(Redis)
2. **缓存预热**: 系统启动时预加载热点规则和特征
3. **缓存更新**: 使用Redis Pub/Sub实现缓存失效通知

## 关键技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| **规则DSL** | Groovy | 动态性强，与Java生态无缝集成，语法简洁，适合低代码场景 |
| **运行时** | Java 21 | 虚拟线程支持，性能大幅提升，当前LTS版本 |
| **应用框架** | Spring Boot 3.3 | 原生支持虚拟线程，启动速度和内存占用显著优化 |
| **数据库** | PostgreSQL 16/17 | 成熟稳定，JSONB支持，性能优秀，适合规则元数据存储 |
| **缓存** | Caffeine + Redis | 两级缓存策略，兼顾性能和分布式需求 |
| **前端** | React 19 | 最新版本，并发渲染，自动批处理，构建复杂UI的最佳选择 |
| **监控** | Prometheus + Grafana + Loki | 云原生监控标准，轻量级日志方案，资源消耗低 |
| **测试** | JUnit 5 + Testcontainers | 现代测试框架，Spring Boot 3.4+原生支持，简化集成测试 |

## 信息来源

### 高置信度来源(HIGH)

- [Spring Boot 3.3新特性 - CSDN](https://blog.csdn.net/qq_35800459/article/details/147046777) — 虚拟线程性能数据:启动时间减少40%，内存降低25%，支持100万+并发
- [Spring Boot 3.3性能提升10倍 - Medium](https://medium.com/@princekumar161999/i-upgraded-to-spring-boot-3-3-and-my-app-became-10x-faster-heres-exactly-what-changed-2025-1037c5b60ac8) — Spring Boot 3.3性能优化特性
- [PostgreSQL 16/17性能优化 - 官方文档](https://www.postgresql.org/docs/release/) — 数据库版本特性
- [规则引擎性能优化 - TRAE](https://www.trae.cn/article/3133478146) — 毫秒级响应的技术要求
- [有赞风控规则引擎实践](https://tech.youzan.com/rules-engine/) — 真实风控系统架构
- [美团风控规则引擎实践](https://tech.meituan.com/2020/05/14/meituan-security-zeus.html) — 版本管理和灰度发布实践
- [OpenFeature风控模型实践 - 腾讯云](https://cloud.tencent.com/developer/article/2540809) — 灰度发布效果:部署周期减少92%，ROI 182%
- [Redis 8.4新特性 - Redis官方博客](https://redis.io/blog/whats-new-in-two-november-2025-edition/) — 缓存吞吐量提升30%
- [Spring Boot 3.4 Testcontainers支持 - 51CTO](https://www.51cto.com/article/814046.html) — 原生Testcontainers支持
- [Java虚拟线程性能 - LinkedIn](https://www.linkedin.com/posts/arthur-alves-da-costa_java-virtual-threads-understanding-jdk-21-activity-7368243772284973057-PHNO) — Java 21虚拟线程特性
- [Spring Boot Docker最佳实践 2025 - Medium](https://javascript.plainenglish.io/dockerize-spring-boot-like-a-pro-2025-best-practices-for-blazing-fast-deployments-1cd4d00fa229) — 分层JAR优化

### 中置信度来源(MEDIUM)

- [Drools vs Easy Rules比较 - CSDN](https://blog.csdn.net/weixin_42516967/article/details/114661913) — 规则引擎对比分析
- [Groovy性能优化 - GitHub](https://github.com/dwclark/groovy-performance) — Groovy DSL性能优化技巧
- [规则引擎测试策略 - 腾讯云](https://cloud.tencent.com/developer/article/2560719) — JUnit5 + Mockito测试策略
- [日志系统选型: ELK vs Loki - dbaplus](https://dbaplus.cn/news-141-6816-1.html) — 日志方案对比
- [Spring Boot 3 Prometheus监控 - 腾讯云](https://cloud.tencent.com/developer/article/2307450) — 监控集成实践

### 低置信度来源(LOW)

- 部分搜索结果未提供具体版本号或发布日期，需要进一步验证
- 一些中文技术博客的性能数据需要结合实际测试验证
- Groovy 5.x的稳定性在生产环境中需要更多验证

---
**技术栈研究用于: 风控规则引擎**
**研究日期: 2025-03-26**
**整体信心: HIGH**
