package com.example.ruleengine.dsl

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.math.BigDecimal
import java.math.BigInteger

/**
 * 条件阈值：对齐前端 `threshold: string | number` 的双态。
 *
 * 设计要点：
 * - 字符串阈值（如枚举值、逗号分隔的 IN 列表、数字字符串 `'1000'`）保持字符串；
 * - 数字阈值用 [BigDecimal] 承载，精确保留 JSON 数字的书写值（整数不带小数点、小数不失真）；
 * - 序列化时经 [JsonValue] 还原为原始 JSON 标量（字符串/数字），保证 round-trip 语义等价。
 */
sealed interface ThresholdValue {
    /** 字符串阈值 */
    data class Text(
        val value: String,
    ) : ThresholdValue {
        @get:JsonValue
        override val raw: Any
            get() = value
    }

    /**
     * 数字阈值。
     *
     * equals 基于数值比较（compareTo），避免 BigDecimal 的 scale 陷阱
     * （如 `1.0` 与 `1.00` 被误判不等）。
     */
    data class Numeric(
        val value: BigDecimal,
    ) : ThresholdValue {
        @get:JsonValue
        override val raw: Any
            get() = value

        override fun equals(other: Any?): Boolean = other is Numeric && value.compareTo(other.value) == 0

        override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

        override fun toString(): String = "Numeric(value=$value)"
    }

    /** 序列化时写回 JSON 的原始标量（String 或 BigDecimal） */
    val raw: Any

    companion object {
        fun of(value: String): Text = Text(value)

        fun of(value: Int): Numeric = Numeric(BigDecimal.valueOf(value.toLong()))

        fun of(value: Long): Numeric = Numeric(BigDecimal.valueOf(value))

        fun of(value: Double): Numeric = Numeric(BigDecimal.valueOf(value))

        fun of(value: BigDecimal): Numeric = Numeric(value)

        /**
         * Jackson 委托式反序列化入口：JSON 字符串 → [Text]，JSON 数字 → [Numeric]。
         * 其他 JSON 标量（布尔等）直接报错，由 Jackson 包装为带位置的解析异常。
         */
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromRaw(raw: Any): ThresholdValue =
            when (raw) {
                is String -> Text(raw)
                is BigDecimal -> Numeric(raw)
                is BigInteger -> Numeric(BigDecimal(raw.toString()))
                is Int, is Long -> Numeric(BigDecimal.valueOf(raw.toLong()))
                is Double, is Float -> Numeric(BigDecimal.valueOf(raw.toDouble()))
                else -> throw IllegalArgumentException("threshold 必须是字符串或数字，实际为: ${raw::class.simpleName}")
            }
    }
}
