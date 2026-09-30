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

    implementation(project(":modules:domain"))
    implementation(project(":modules:dsl"))
    implementation(project(":modules:engine"))
    implementation(project(":modules:shared"))

    testImplementation(libs.spring.boot.starter.test)
}
