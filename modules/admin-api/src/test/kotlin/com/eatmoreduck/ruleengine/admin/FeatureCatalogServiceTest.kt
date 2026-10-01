package com.eatmoreduck.ruleengine.admin

import com.eatmoreduck.ruleengine.admin.dto.FeatureDefinitionRequest
import com.eatmoreduck.ruleengine.admin.dto.FeatureValidationRequest
import com.eatmoreduck.ruleengine.admin.feature.FeatureCatalogService
import com.eatmoreduck.ruleengine.admin.grayscale.DecisionFlowMain
import com.eatmoreduck.ruleengine.admin.rules.RuleAssembler
import com.eatmoreduck.ruleengine.admin.rules.RulePayloadValidator
import com.eatmoreduck.ruleengine.admin.rules.RuleService
import com.eatmoreduck.ruleengine.engine.GroovyScriptEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 特征目录服务单元测试（内存仓储）。
 * 覆盖：编码归一与唯一性、别名归属校验、别名替换式更新、
 * 校验通道（别名解析 + 运算符兼容告警）、引用扫描（规则 + 决策流）。
 */
@DisplayName("特征目录服务")
class FeatureCatalogServiceTest {
    private lateinit var featureRepository: FakeFeatureCatalogRepository
    private lateinit var ruleRepository: FakeRuleRepository
    private lateinit var versionRepository: FakeRuleVersionRepository
    private lateinit var flowSupport: FakeDecisionFlowSupportRepository
    private lateinit var service: FeatureCatalogService

    /** 缓存失效广播观测桩（构造服务必须项，当前用例不做事件断言） */
    private val eventPublisher = RecordingEventPublisher()

    @BeforeEach
    fun setUp() {
        featureRepository = FakeFeatureCatalogRepository()
        ruleRepository = FakeRuleRepository()
        versionRepository = FakeRuleVersionRepository()
        flowSupport = FakeDecisionFlowSupportRepository()
        service = FeatureCatalogService(featureRepository, ruleRepository, versionRepository, flowSupport, eventPublisher)
    }

    private fun createRequest(
        code: String = "order_amount",
        aliases: List<String> = listOf("amt"),
    ) = FeatureDefinitionRequest(
        code = code,
        name = "订单金额",
        dataType = "NUMBER",
        sourceType = "INPUT",
        exampleValue = "100",
        scope = "ORDER",
        aliases = aliases,
    )

    @Test
    fun `创建特征含别名并回显完整字段`() {
        val response = service.createDefinition(createRequest())
        assertEquals("order_amount", response.code)
        assertEquals("NUMBER", response.dataType)
        assertEquals("NORMAL", response.sensitivity)
        assertEquals("ACTIVE", response.status)
        assertEquals(listOf("amt"), response.aliases)
        // 别名可解析到规范编码
        assertEquals("order_amount", featureRepository.resolveCode("amt")!!.code)
    }

    @Test
    fun `重复编码与大小写变体都被拒绝`() {
        service.createDefinition(createRequest())
        val error =
            assertThrows<IllegalArgumentException> {
                service.createDefinition(createRequest(code = "ORDER_AMOUNT"))
            }
        assertEquals("特征编码已存在: ORDER_AMOUNT", error.message)
    }

    @Test
    fun `别名归属校验：等于编码、与已有编码冲突、被其他特征占用`() {
        service.createDefinition(createRequest())

        val sameAsCode =
            assertThrows<IllegalArgumentException> {
                service.createDefinition(createRequest(code = "user_level", aliases = listOf("user_level")))
            }
        assertEquals("别名不能与特征编码相同: user_level", sameAsCode.message)

        val conflictWithDefinition =
            assertThrows<IllegalArgumentException> {
                service.createDefinition(createRequest(code = "user_level", aliases = listOf("order_amount")))
            }
        assertEquals("别名与已有特征编码冲突: order_amount", conflictWithDefinition.message)

        val occupiedByOther =
            assertThrows<IllegalArgumentException> {
                service.createDefinition(createRequest(code = "user_level", aliases = listOf("amt")))
            }
        assertEquals("别名已被其他特征占用: amt", occupiedByOther.message)
    }

    @Test
    fun `更新特征：编码不可改、别名全量替换`() {
        service.createDefinition(createRequest())
        val updated =
            service.updateDefinition(
                "order_amount",
                createRequest(aliases = listOf("amt", "pay_amount")).copy(name = "订单总金额"),
            )
        assertEquals("订单总金额", updated.name)
        assertEquals(listOf("amt", "pay_amount"), updated.aliases)
        // 替换后的别名仍可解析
        assertEquals("order_amount", featureRepository.resolveCode("pay_amount")!!.code)

        val codeChange =
            assertThrows<IllegalArgumentException> {
                service.updateDefinition("order_amount", createRequest(code = "new_code"))
            }
        assertEquals("特征编码不允许修改", codeChange.message)
    }

    @Test
    fun `校验通道：别名解析告警与非 ACTIVE 状态告警`() {
        service.createDefinition(createRequest())

        val result =
            service.validate(
                FeatureValidationRequest(
                    items =
                        listOf(
                            FeatureValidationRequest.Item(fieldName = "amt"),
                        ),
                ),
            )
        assertTrue(result.valid)
        val item = result.items.single()
        assertTrue(item.found)
        assertTrue(item.matchedByAlias)
        assertEquals("order_amount", item.canonicalCode)
        assertEquals("amt", item.matchedAlias)
        // 别名映射告警进入 warnings
        assertTrue(result.warnings.any { it.contains("已映射为标准特征") })

        val unknown =
            service.validate(
                FeatureValidationRequest(items = listOf(FeatureValidationRequest.Item(fieldName = "ghost_field"))),
            )
        assertFalse(unknown.valid)
        assertEquals(listOf("ghost_field"), unknown.unknownFields)
        assertEquals(listOf("字段 ghost_field 未收录于特征字典"), unknown.warnings)
    }

    @Test
    fun `运算符兼容性告警照搬旧实现`() {
        service.createDefinition(createRequest())
        service.createDefinition(
            FeatureDefinitionRequest(
                code = "region",
                name = "地区",
                dataType = "STRING",
                sourceType = "INPUT",
                aliases = emptyList(),
            ),
        )

        val result =
            service.validate(
                FeatureValidationRequest(
                    items =
                        listOf(
                            // 字符串特征 + 数值运算符 → "应为数值类型" 告警
                            FeatureValidationRequest.Item(fieldName = "region", operator = "GT", threshold = 1),
                            // 数值特征 + 合法数值运算符 → 无兼容性告警
                            FeatureValidationRequest.Item(fieldName = "order_amount", operator = "GT", threshold = 100),
                        ),
                ),
            )
        assertFalse(result.valid)
        val regionItem = result.items.first { it.fieldName == "region" }
        assertTrue(regionItem.warnings.any { it.contains("应为数值类型") })
        val amountItem = result.items.first { it.fieldName == "order_amount" }
        assertTrue(amountItem.warnings.none { it.contains("不匹配") })
    }

    @Test
    fun `引用扫描覆盖规则脚本与决策流条件节点并按类型排序`() {
        service.createDefinition(createRequest())
        val ruleService =
            RuleService(
                ruleRepository,
                versionRepository,
                RulePayloadValidator(GroovyScriptEngine()),
                RuleAssembler(),
                flowSupport,
                RecordingEventPublisher(),
            )
        ruleService.createRule(
            com.eatmoreduck.ruleengine.admin.dto.CreateRuleRequest(
                ruleKey = "amount_rule",
                ruleName = "金额规则",
                groovyScript = "def amt = features.order_amount\nreturn 'PASS'",
            ),
            "tester",
        )
        flowSupport.flows["flow_a"] =
            DecisionFlowMain(id = 9L, flowKey = "flow_a", flowName = "金额流", flowGraph = "{}", version = 1, activeVersion = 1)

        val references = service.getReferences("order_amount")
        // features.orderAmount 与特征编码 order_amount 大小写不敏感相交
        assertTrue(references.any { it.type == "rule" && it.key == "amount_rule" })
    }

    @Test
    fun `查询不存在的特征返回旧契约消息`() {
        val error =
            assertThrows<IllegalArgumentException> {
                service.getDefinition("nope")
            }
        assertEquals("特征不存在: nope", error.message)
    }

    @Test
    fun `目录检索按数据类型过滤并分页`() {
        service.createDefinition(createRequest())
        service.createDefinition(
            FeatureDefinitionRequest(code = "region", name = "地区", dataType = "STRING", sourceType = "INPUT"),
        )
        val page =
            service.searchDefinitions(
                0,
                20,
                keyword = null,
                scope = null,
                dataType = "number",
                sourceType = null,
                sensitivity = null,
                status = null,
            )
        assertEquals(1, page.totalElements)
        assertEquals("order_amount", page.content.single().code)
    }
}
