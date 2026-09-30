// Spring Boot 可部署物通用约定：在 Kotlin 库约定之上叠加 Boot 插件与协程编译器配置
// 依赖声明放在各 app 模块的 build.gradle.kts 中（模块内可正常使用 libs.* 访问器）

plugins {
    id("ruleengine.kotlin-library")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

kotlin {
    compilerOptions {
        // 对 Spring 的 @Nullable/@NonNull 元注解做严格空安全检查
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
