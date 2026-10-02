// Spring 组件库模块约定：库模块（无 Boot 启动类/打包）+ kotlin-spring all-open。
// 用途：被部署物吸入同一上下文的 Spring 组件集（如 decision-api 合并进 admin-api 后仍可回拆）。
// 必须保留 all-open：@Component/@Repository 组件以 open 编译，避免消费侧
// PersistenceExceptionTranslation / 事务代理生成 CGLIB 子类时因 final 失败
// （storage 的 kotlin-library 无 all-open，其 final @Repository 需部署侧显式排除+委托注册，即此坑的先例）。

plugins {
    id("ruleengine.kotlin-library")
    kotlin("plugin.spring")
}

kotlin {
    compilerOptions {
        // 对 Spring 的 @Nullable/@NonNull 元注解做严格空安全检查（与 spring-app 一致）
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
