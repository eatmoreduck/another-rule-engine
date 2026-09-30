// 根项目：仅做聚合，不放任何代码。
// 编译选项、格式化、测试框架等约定统一由 build-logic 中的预编译脚本插件提供：
//   - ruleengine.kotlin-library：Kotlin 库模块通用约定
//   - ruleengine.spring-app   ：Spring Boot 可部署物通用约定
plugins {
    base
}
