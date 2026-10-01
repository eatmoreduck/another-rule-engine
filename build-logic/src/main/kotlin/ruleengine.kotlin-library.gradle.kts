// Kotlin 库模块通用约定：JDK 25 工具链、JUnit 5、Spotless/ktlint 格式化
// 注意：预编译脚本插件内无法使用 libs.* 类型安全访问器，版本号经 VersionCatalogsExtension 读取

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    kotlin("jvm")
    id("com.diffplug.spotless")
}

group = "com.eatmoreduck.ruleengine"
version = "0.1.0-SNAPSHOT"

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

kotlin {
    // 工具链自动探测 SDKMAN 安装的 JDK 25（本机为 25.0.1-graalce）
    jvmToolchain(25)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    kotlin {
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
    }
    kotlinGradle {
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
    }
}
