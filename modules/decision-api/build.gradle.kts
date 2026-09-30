// 部署物 1：决策服务（50ms 决策链路，无状态，HPA 按 QPS 扩缩）
plugins {
    id("ruleengine.spring-app")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.kotlinx.coroutines.core)
    // Spring MVC 的 suspend controller 依赖 coroutines-reactor 桥接
    implementation(libs.kotlinx.coroutines.reactor)

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.registry.prometheus)

    // Bean Validation（@NotBlank 等请求校验，错误消息与旧契约一致）
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // 认证：Sa-Token（官方 Boot 4 starter，与 admin-api 同一套 sys_* 权限数据）
    implementation(libs.sa.token.spring.boot4.starter)

    // 阶段 5：Redis 集成——
    // - spring-boot-starter-data-redis：Lettuce 连接 + StringRedisTemplate（缓存失效广播订阅）
    // - sa-token-redis-jackson（聚合依赖，实际生效的是其携带的 sa-token-redis-template）：
    //   官方 SaTokenDaoForRedisTemplate 自动装配，会话落 Redis 实现跨服务共享。
    //   ⚠️ 排除 sa-token-jackson（Jackson 2 SPI 插件）：Boot 4 的 Jackson 3 插件
    //   （sa-token-jackson3，Boot4 starter 自带）与其共存时插件装载顺序不确定，
    //   Jackson 2 插件先装会因 tools.jackson 类路径缺 Jackson 2 命名空间而崩溃（探针结论）
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.sa.token.redis.jackson) {
        exclude(group = "cn.dev33", module = "sa-token-jackson")
    }

    // Web 层 JSON：Jackson 3（tools.jackson 命名空间）+ Kotlin 模块（data class 绑定）
    implementation(libs.bundles.jackson)

    // 规则/流图/灰度/特征/日志仓储的 Exposed 访问（复用 storage 的事务设施）
    implementation(libs.bundles.exposed)
    // Spring 事务桥接（@Transactional ↔ Exposed，经 storage 的 SpringTransactionManager）
    implementation(libs.spring.transaction)
    // Boot 4 拆分出的事务自动装配模块（激活 @Transactional 代理拦截；版本由 BOM 管理）
    implementation("org.springframework.boot:spring-boot-transaction")

    // 快照缓存（规则/流图/灰度/特征值/黑白名单五层 Caffeine）
    implementation(libs.caffeine)

    implementation(project(":modules:domain"))
    implementation(project(":modules:dsl"))
    implementation(project(":modules:engine"))
    implementation(project(":modules:storage"))
    implementation(project(":modules:shared"))

    // 生产运行时的 JDBC 驱动（storage 的 testImplementation 只覆盖测试类路径）
    runtimeOnly(libs.postgresql)
    // JSON 结构化日志（SPRING_PROFILES_ACTIVE=json 时启用，供 Logstash 采集）
    runtimeOnly(libs.logstash.logback.encoder)

    testImplementation(libs.h2)

    // 契约测试：Testcontainers PG16 起真实库（Flyway 全量迁移 + 种子数据）
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)

    testImplementation(libs.spring.boot.starter.test)
}
