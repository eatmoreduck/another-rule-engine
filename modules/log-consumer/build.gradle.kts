// 部署物 3：日志消费服务（消费 Kafka 决策事件，批量落库，阶段 4 启用）
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
    implementation(project(":modules:storage"))
    implementation(project(":modules:shared"))

    testImplementation(libs.spring.boot.starter.test)
}
