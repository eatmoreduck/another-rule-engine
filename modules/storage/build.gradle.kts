// 持久化层：Exposed 表定义、仓储实现、Flyway 基线与集成测试
plugins {
    id("ruleengine.kotlin-library")
}

dependencies {
    // Spring 注解（@Configuration/@Bean/@ConfigurationProperties）与 Hikari 连接池版本由 Boot BOM 管理；
    // 不引 starter（storage 是库模块，装配入口由部署模块决定）
    implementation(platform(libs.spring.boot.dependencies))
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot")
    implementation("com.zaxxer:HikariCP")

    implementation(libs.bundles.exposed)
    implementation(project(":modules:domain"))
    // Exposed 的 Spring 事务集成（Boot 4.1 运行时兼容性已经探针验证通过）
    implementation(libs.spring.transaction)

    // Flyway 基线：沿用旧后端迁移脚本（classpath:db/migration）
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // 灰度策略 feature_rules JSON 编解码（与 dsl 模块同一套 Jackson 3 事实源）
    implementation(libs.bundles.jackson)

    testImplementation(libs.h2)
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
