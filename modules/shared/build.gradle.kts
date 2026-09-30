// 共享设施层：缓存失效事件契约（纯契约，零 Spring 依赖）+ Sa-Token Redis 会话降级设施
plugins {
    id("ruleengine.kotlin-library")
}

dependencies {
    // 缓存失效事件的 JSON 编解码：Jackson 3（tools.jackson 命名空间）+ Kotlin 模块。
    // Jackson 非 Spring 依赖，缓存契约包保持纯契约语义
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.bundles.jackson)

    // Sa-Token 会话降级设施仅引用 sa-token-core 类型（SaTokenDao/SaSession）与 spring-data-redis
    // 的异常类型，运行时一律由部署物自带的依赖提供，故 compileOnly：shared 不向下游传递
    compileOnly(libs.sa.token.spring.boot4.starter)
    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly("org.springframework.data:spring-data-redis")

    testImplementation(libs.sa.token.spring.boot4.starter)
    // 会话降级链路测试：官方 SaTokenDaoForRedisTemplate（redis-jackson 聚合依赖携带，排除其
    // Jackson 2 插件——该插件在 Boot 4 的 SPI 顺序不确定，存在启动崩溃风险，部署物同款排除）
    testImplementation(libs.sa.token.redis.jackson) {
        exclude(group = "cn.dev33", module = "sa-token-jackson")
    }
    testImplementation(libs.spring.boot.starter.data.redis)
    testImplementation(libs.spring.boot.starter.test)

    // Testcontainers Redis（org.testcontainers.testcontainers 已在版本目录，GenericContainer 即可）
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
}
