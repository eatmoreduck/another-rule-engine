package com.eatmoreduck.ruleengine.dsl

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * Jackson 3（tools.jackson 命名空间）ObjectMapper 单例。
 *
 * - 经 builder 构造（Jackson 3 的 ObjectMapper 不可变，不支持直接 setter）；
 * - 注册 Kotlin 模块，支持 data class 经主构造器反序列化与不可空性校验；
 * - Jackson 3 默认已禁用 FAIL_ON_UNKNOWN_PROPERTIES，天然容忍前端新增的未知字段，
 *   此处不额外开启任何严格化开关。
 */
object DslJson {
    val mapper: ObjectMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .build()

    /** 序列化为 JSON 字符串（紧凑格式） */
    fun write(value: Any): String = mapper.writeValueAsString(value)

    /** 反序列化为指定类型；失败抛出 JacksonException（非受检），解析 API 层负责转换为结果类型 */
    fun <T : Any> read(
        json: String,
        type: Class<T>,
    ): T = mapper.readValue(json, type)

    /** reified 便捷重载 */
    inline fun <reified T : Any> read(json: String): T = read(json, T::class.java)
}
