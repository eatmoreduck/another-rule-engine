// 部署物 2：管理服务（规则 CRUD/版本/灰度/特征目录/AI 生成）
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

    implementation(project(":modules:domain"))
    implementation(project(":modules:dsl"))
    implementation(project(":modules:engine"))
    implementation(project(":modules:storage"))
    implementation(project(":modules:shared"))

    // 阶段 2 探针：Exposed × Boot 4 运行时兼容性验证（通过后转正式集成方案）
    testImplementation(libs.spring.transaction)
    testImplementation(libs.h2)

    testImplementation(libs.spring.boot.starter.test)
}
