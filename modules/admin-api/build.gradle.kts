// 部署物 2：管理服务（登录认证/规则 CRUD/版本管理/灰度发布/特征目录）
import org.gradle.testing.jacoco.tasks.JacocoReport

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

    // 认证：Sa-Token（官方 Boot 4 starter）+ BCrypt 密码哈希（沿用旧实现语义）
    implementation(libs.sa.token.spring.boot4.starter)
    implementation("org.springframework.security:spring-security-crypto")

    // 阶段 5：Redis 集成——
    // - spring-boot-starter-data-redis：Lettuce 连接 + StringRedisTemplate（缓存失效事件发布）
    // - sa-token-redis-jackson（聚合依赖，实际生效的是其携带的 sa-token-redis-template）：
    //   官方 SaTokenDaoForRedisTemplate 自动装配，会话落 Redis 实现跨服务共享。
    //   ⚠️ 排除 sa-token-jackson（Jackson 2 SPI 插件）：Boot 4 的 Jackson 3 插件
    //   （sa-token-jackson3，Boot4 starter 自带）与其共存时插件装载顺序不确定，
    //   Jackson 2 插件先装会因 tools.jackson 类路径缺 Jackson 2 命名空间而崩溃（探针结论）
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.sa.token.redis.jackson) {
        exclude(group = "cn.dev33", module = "sa-token-jackson")
    }

    // 登录失败锁定缓存（旧 AuthService 的 Caffeine 语义）
    implementation(libs.caffeine)

    // Web 层 JSON：Jackson 3（tools.jackson 命名空间）+ Kotlin 模块（data class 绑定）
    implementation(libs.bundles.jackson)

    // 认证域（sys_* 表）与决策流支撑表对象的 Exposed 访问（复用 storage 的事务设施）
    implementation(libs.bundles.exposed)
    // Spring 事务桥接（@Transactional ↔ Exposed，经 storage 的 SpringTransactionManager）
    implementation(libs.spring.transaction)
    // Boot 4 拆分出的事务自动装配模块（激活 @Transactional 代理拦截；版本由 BOM 管理）
    implementation("org.springframework.boot:spring-boot-transaction")

    implementation(project(":modules:domain"))
    implementation(project(":modules:dsl"))
    // 决策链路组件（合并部署物：决策执行/特征解析/灰度路由由根包扫描吸入同一上下文）
    implementation(project(":modules:decision-api"))
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

// 合并部署物聚合覆盖率：决策组件（modules/decision-api）的真实执行数据由本模块的
// 契约测试产生，但 Gradle 的 jacocoTestReport 只统计本模块 sourceSets——
// 该聚合任务把 decision 的 class/源码纳入统计口径，还原决策链路真实覆盖率。
val decisionModule = project(":modules:decision-api")

tasks.register<JacocoReport>("jacocoAggregateReport") {
    dependsOn(tasks.test)
    executionData(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
    sourceDirectories.from(files("src/main/kotlin", decisionModule.file("src/main/kotlin")))
    classDirectories.from(
        files(
            layout.buildDirectory.dir("classes/kotlin/main"),
            decisionModule.layout.buildDirectory.dir("classes/kotlin/main"),
        ),
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
