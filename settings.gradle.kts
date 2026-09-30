// 多模块构建入口：三个可部署物（decision-api / admin-api / log-consumer）+ 五个共享库
// 约定（编译器选项、格式化、测试框架）统一由 build-logic 中的预编译脚本插件提供

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "another-rule-engine"

include(
    "modules:domain",
    "modules:dsl",
    "modules:engine",
    "modules:storage",
    "modules:shared",
    "modules:decision-api",
    "modules:admin-api",
    "modules:log-consumer",
)
