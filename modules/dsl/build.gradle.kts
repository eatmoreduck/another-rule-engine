// 规则 DSL 模型层：与前端 dslGenerator/dslParser 对应的结构定义（阶段 1 填充）
// 负责：规则定义树 / 决策流图的结构模型、Jackson 3 序列化、解析 API 与结构校验
plugins {
    id("ruleengine.kotlin-library")
}

dependencies {
    implementation(libs.bundles.jackson)
}
