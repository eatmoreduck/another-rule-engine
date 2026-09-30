// 脚本引擎层：Groovy DSL 编译、缓存、沙箱校验、类加载隔离（阶段 1 核心）
plugins {
    id("ruleengine.kotlin-library")
}

dependencies {
    implementation(libs.bundles.groovy)
}
