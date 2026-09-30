package com.example.ruleengine.admin

import com.example.ruleengine.admin.dto.CreateDecisionFlowRequest
import com.example.ruleengine.admin.dto.CreateFlowVersionRequest
import com.example.ruleengine.admin.dto.DecisionFlowQuery
import com.example.ruleengine.admin.dto.FlowRollbackRequest
import com.example.ruleengine.admin.dto.UpdateDecisionFlowRequest
import com.example.ruleengine.admin.flows.DecisionFlowService
import com.example.ruleengine.admin.flows.FlowGraphPayloadValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 决策流生命周期与版本管理服务单元测试（内存仓储）。
 * 覆盖：创建（初始版本行 + 图校验）、更新（元数据合并 / 图变更推进指针）、启停与软删、
 * 多条件查询、草稿版本与发布守卫（2b 语义）、回滚（更高版本号副本 + 立即生效）。
 */
@DisplayName("决策流管理服务")
class DecisionFlowServiceTest {
    private lateinit var repository: FakeDecisionFlowRepository
    private lateinit var service: DecisionFlowService

    /** 合法流程图（start → ruleset → end，各节点 type 与 data.nodeType 一致） */
    private val graphV1 =
        """{"nodes":[
            {"id":"start-1","type":"start","position":{"x":0,"y":0},"data":{"label":"开始","nodeType":"start"}},
            {"id":"ruleset-1","type":"ruleset","position":{"x":100,"y":0},"data":{"label":"规则集","nodeType":"ruleset","ruleKeys":["r1"]}},
            {"id":"end-1","type":"end","position":{"x":200,"y":0},"data":{"label":"结束","nodeType":"end","defaultAction":"PASS","defaultReason":"通过"}}
        ],"edges":[
            {"id":"e1","source":"start-1","target":"ruleset-1"},
            {"id":"e2","source":"ruleset-1","target":"end-1"}
        ]}"""

    /** 非法流程图（两个 start 节点，ERROR 级） */
    private val badGraph =
        """{"nodes":[
            {"id":"start-1","type":"start","position":{"x":0,"y":0},"data":{"label":"开始","nodeType":"start"}},
            {"id":"start-2","type":"start","position":{"x":50,"y":0},"data":{"label":"开始2","nodeType":"start"}}
        ],"edges":[]}"""

    @BeforeEach
    fun setUp() {
        repository = FakeDecisionFlowRepository()
        service = DecisionFlowService(repository, FlowGraphPayloadValidator())
    }

    private fun createFlow(
        flowKey: String = "flow_a",
        flowGraph: String = graphV1,
    ) = service.createFlow(
        CreateDecisionFlowRequest(flowKey = flowKey, flowName = "流程A", flowGraph = flowGraph),
        "tester",
    )

    @Test
    fun `创建决策流落主表与初始版本行`() {
        val response = createFlow()

        assertEquals("flow_a", response.flowKey)
        assertEquals(1, response.version)
        assertEquals("DRAFT", response.status)
        assertTrue(response.enabled)
        assertEquals(0L, response.optLockVersion)
        // 初始版本行：状态 ACTIVE（照旧），changeReason 固定
        val version = repository.findVersion("flow_a", 1)!!
        assertEquals("ACTIVE", version.status)
        assertEquals("创建决策流", version.changeReason)
        assertFalse(version.isRollback)
        assertNotNull(version.id)
        assertEquals(response.id, repository.findMain("flow_a")!!.id)
    }

    @Test
    fun `重复创建与非法图创建被拒绝`() {
        createFlow()

        val dup =
            assertThrows<IllegalArgumentException> {
                createFlow()
            }
        assertEquals("决策流Key已存在: flow_a", dup.message)

        val invalidGraph =
            assertThrows<IllegalArgumentException> {
                createFlow("flow_bad", badGraph)
            }
        assertTrue(invalidGraph.message!!.startsWith("流程图数据非法: "), invalidGraph.message)
        assertNull(repository.findMain("flow_bad"))

        val brokenJson =
            assertThrows<IllegalArgumentException> {
                createFlow("flow_broken", """{"nodes": broken""")
            }
        assertTrue(brokenJson.message!!.startsWith("流程图数据非法: "), brokenJson.message)
    }

    @Test
    fun `仅改元数据不推进版本号`() {
        createFlow()

        val updated =
            service.updateFlow(
                "flow_a",
                UpdateDecisionFlowRequest(flowName = "改名", flowDescription = "新描述"),
                "editor",
            )

        assertEquals("改名", updated.flowName)
        assertEquals("新描述", updated.flowDescription)
        assertEquals(1, updated.version)
        assertEquals(graphV1, repository.findVersion("flow_a", 1)!!.flowGraph)
    }

    @Test
    fun `图变更推进版本并归档旧 ACTIVE 且指针一致`() {
        createFlow()

        val updated =
            service.updateFlow(
                "flow_a",
                UpdateDecisionFlowRequest(flowGraph = graphV2(), changeReason = "调阈值"),
                "editor",
            )

        assertEquals(2, updated.version)
        assertEquals(2, updated.activeVersion)
        assertEquals(graphV2(), updated.flowGraph)
        // 旧版本归档、新版本 ACTIVE
        assertEquals("ARCHIVED", repository.findVersion("flow_a", 1)!!.status)
        assertEquals("ACTIVE", repository.findVersion("flow_a", 2)!!.status)
        assertEquals("调阈值", repository.findVersion("flow_a", 2)!!.changeReason)
        // 元数据 null 不覆盖（照旧逐字段判空）
        assertEquals("流程A", updated.flowName)
    }

    private fun graphV2() =
        """{"nodes":[
            {"id":"start-1","type":"start","position":{"x":0,"y":0},"data":{"label":"开始","nodeType":"start"}},
            {"id":"end-1","type":"end","position":{"x":200,"y":0},"data":{"label":"结束","nodeType":"end","defaultAction":"MANUAL_REVIEW","defaultReason":"人工"}}
        ],"edges":[
            {"id":"e1","source":"start-1","target":"end-1"}
        ]}"""

    @Test
    fun `图未变化时不推进版本`() {
        createFlow()

        val updated = service.updateFlow("flow_a", UpdateDecisionFlowRequest(flowGraph = graphV1), "editor")

        assertEquals(1, updated.version)
        assertEquals(1, repository.findVersionsByFlowKey("flow_a").size)
    }

    @Test
    fun `图变更前校验失败拒绝并保持原状`() {
        createFlow()

        assertThrows<IllegalArgumentException> {
            service.updateFlow("flow_a", UpdateDecisionFlowRequest(flowGraph = badGraph), "editor")
        }
        assertEquals(1, repository.findMain("flow_a")!!.version)
        assertEquals(1, repository.findVersionsByFlowKey("flow_a").size)
    }

    @Test
    fun `启停与软删除语义照旧`() {
        createFlow()

        val disabled = service.disableFlow("flow_a", "editor")
        assertFalse(disabled.enabled)
        assertEquals("DRAFT", disabled.status)

        val enabled = service.enableFlow("flow_a", "editor")
        assertTrue(enabled.enabled)
        assertEquals("ACTIVE", enabled.status)

        service.deleteFlow("flow_a", "editor")
        val deleted = repository.findMain("flow_a")!!
        assertEquals("DELETED", deleted.status)
        assertFalse(deleted.enabled)
    }

    @Test
    fun `未知流程统一报决策流不存在`() {
        val calls: List<() -> Unit> =
            listOf(
                {
                    service.updateFlow("ghost", UpdateDecisionFlowRequest(flowName = "x"), "e")
                    Unit
                },
                {
                    service.deleteFlow("ghost", "e")
                    Unit
                },
                {
                    service.enableFlow("ghost", "e")
                    Unit
                },
                {
                    service.disableFlow("ghost", "e")
                    Unit
                },
                {
                    service.getFlow("ghost")
                    Unit
                },
                {
                    service.createDraftVersion("ghost", CreateFlowVersionRequest(flowGraph = graphV1), "e")
                    Unit
                },
                {
                    service.publishVersion("ghost", 1, "e")
                    Unit
                },
                {
                    service.rollback("ghost", FlowRollbackRequest(targetVersion = 1), "e")
                    Unit
                },
            )
        for (call in calls) {
            val e = assertThrows<IllegalArgumentException>(call)
            assertEquals("决策流不存在: ghost", e.message)
        }
    }

    @Test
    fun `多条件查询过滤语义`() {
        createFlow("flow_a")
        createFlow("flow_b")
        service.disableFlow("flow_b", "editor")

        // keyword 命中 flowKey
        assertEquals(1, service.queryFlows(DecisionFlowQuery(keyword = "flow_a"), 0, 20).totalElements)
        // enabled 过滤
        assertEquals(1, service.queryFlows(DecisionFlowQuery(enabled = false), 0, 20).totalElements)
        assertEquals(1, service.queryFlows(DecisionFlowQuery(enabled = true), 0, 20).totalElements)
        // status 过滤（软删后仍可见——照旧不过滤 DELETED）
        assertEquals(2, service.queryFlows(DecisionFlowQuery(status = "DRAFT"), 0, 20).totalElements)
        service.deleteFlow("flow_b", "editor")
        assertEquals(1, service.queryFlows(DecisionFlowQuery(status = "DELETED"), 0, 20).totalElements)
        assertEquals(2, service.queryFlows(DecisionFlowQuery(), 0, 20).totalElements)
        // createdBy 精确
        assertEquals(2, service.queryFlows(DecisionFlowQuery(createdBy = "tester"), 0, 20).totalElements)
        assertEquals(0, service.queryFlows(DecisionFlowQuery(createdBy = "nobody"), 0, 20).totalElements)
    }

    @Test
    fun `列表分页与未知流程返回空版本列表`() {
        createFlow("flow_a")
        createFlow("flow_b")

        val page = service.listFlows(0, 1)
        assertEquals(2, page.totalElements)
        assertEquals(1, page.content.size)
        assertEquals(2, page.totalPages)

        assertTrue(service.getVersions("ghost").isEmpty())
        assertNull(service.getVersion("ghost", 1))
    }

    @Test
    fun `草稿版本不改变生效指针且发布需状态迁移`() {
        createFlow()

        val draft = service.createDraftVersion("flow_a", CreateFlowVersionRequest(flowGraph = graphV2(), changeReason = "草稿"), "editor")

        assertEquals(2, draft.version)
        assertEquals("DRAFT", draft.status)
        // 主表内容版本号推进，生效指针与流程图不动
        assertEquals(2, repository.findMain("flow_a")!!.version)
        assertNull(repository.findMain("flow_a")!!.activeVersion)
        assertEquals(graphV1, repository.findMain("flow_a")!!.flowGraph)
        assertTrue(repository.findMain("flow_a")!!.enabled)

        // 发布非草稿版本 → 守卫（消息逐字）
        val guard =
            assertThrows<IllegalArgumentException> {
                service.publishVersion("flow_a", 1, "editor")
            }
        assertEquals("只有草稿或灰度中的版本才能发布，当前状态: ACTIVE", guard.message)

        // 发布草稿 → ACTIVE + 指针推进
        val published = service.publishVersion("flow_a", 2, "editor")
        assertEquals("ACTIVE", published.status)
        assertEquals(2, repository.findMain("flow_a")!!.activeVersion)
        assertEquals(graphV2(), repository.findMain("flow_a")!!.flowGraph)
        assertEquals("ARCHIVED", repository.findVersion("flow_a", 1)!!.status)

        // 重复发布 ACTIVE 版本 → 守卫
        val repeat =
            assertThrows<IllegalArgumentException> {
                service.publishVersion("flow_a", 2, "editor")
            }
        assertEquals("只有草稿或灰度中的版本才能发布，当前状态: ACTIVE", repeat.message)

        // 发布不存在的版本 → 守卫
        val missing =
            assertThrows<IllegalArgumentException> {
                service.publishVersion("flow_a", 9, "editor")
            }
        assertEquals("版本不存在: 9", missing.message)
    }

    @Test
    fun `回滚以更高版本号落地目标副本并立即生效`() {
        createFlow()
        service.updateFlow("flow_a", UpdateDecisionFlowRequest(flowGraph = graphV2()), "editor")

        val rollback = service.rollback("flow_a", FlowRollbackRequest(targetVersion = 1), "editor")

        // 更高版本号副本：v3 = v1 内容
        assertEquals(3, rollback.version)
        assertEquals("ACTIVE", rollback.status)
        assertTrue(rollback.isRollback)
        assertEquals(1, rollback.rollbackFromVersion)
        assertEquals("回滚到版本 1", rollback.changeReason)
        assertEquals(graphV1, rollback.flowGraph)
        // 主表立即生效（归档旧 ACTIVE + 指针推进）
        assertEquals(3, repository.findMain("flow_a")!!.activeVersion)
        assertEquals(graphV1, repository.findMain("flow_a")!!.flowGraph)
        assertEquals("ARCHIVED", repository.findVersion("flow_a", 2)!!.status)

        // 目标版本不存在 → 守卫
        val missing =
            assertThrows<IllegalArgumentException> {
                service.rollback("flow_a", FlowRollbackRequest(targetVersion = 9), "editor")
            }
        assertEquals("版本不存在: 9", missing.message)
    }
}
