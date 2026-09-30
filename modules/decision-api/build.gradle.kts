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
