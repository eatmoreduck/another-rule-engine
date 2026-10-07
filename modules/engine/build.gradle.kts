// 脚本引擎层：Groovy DSL 编译、缓存、沙箱校验、类加载隔离（阶段 1 核心）
plugins {
    id("ruleengine.kotlin-library")
}

dependencies {
    implementation(libs.bundles.groovy)
    // 编译缓存（key = SHA-256(沙箱配置版本 + 脚本文本)，命中不重复编译）
    implementation(libs.caffeine)
    // 衍生特征公式表达式引擎（AviatorScript，编译为字节码）
    implementation(libs.aviator)
}
