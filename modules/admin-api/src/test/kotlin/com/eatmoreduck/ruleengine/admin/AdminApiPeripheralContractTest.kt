package com.eatmoreduck.ruleengine.admin

import cn.dev33.satoken.filter.SaFirewallCheckFilterForJakartaServlet
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet
import com.eatmoreduck.ruleengine.admin.auth.SysRolesTable
import com.eatmoreduck.ruleengine.admin.auth.SysUserRolesTable
import com.eatmoreduck.ruleengine.admin.auth.SysUsersTable
import com.eatmoreduck.ruleengine.admin.data.AuditLogsTable
import com.eatmoreduck.ruleengine.dsl.DslJson
import com.eatmoreduck.ruleengine.storage.table.RulesTable
import org.hamcrest.Matchers
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

/**
 * admin-api 外围接口（阶段 2c）契约测试：决策流管理 + 版本 + 黑白名单 + 审计日志 + 环境管理。
 *
 * 与 AdminApiContractTest 同款设施：Testcontainers PG16（真实 PostgreSQL + Flyway V1..V25
 * 迁移 + 权限种子）+ 完整 Spring 上下文（Sa-Token 拦截链）+ MockMvc；断言口径为前端
 * 消费方（frontend/src/api 与 frontend/src/types）的字段名与解析逻辑；
 * 无 Docker 的环境自动跳过整个类。
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@SpringBootTest
@DisplayName("admin-api 契约：决策流管理 + 黑白名单 + 审计日志 + 环境管理")
class AdminApiPeripheralContractTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("ruleengine")
                .withUsername("test")
                .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("ruleengine.storage.url", postgres::getJdbcUrl)
            registry.add("ruleengine.storage.username", postgres::getUsername)
            registry.add("ruleengine.storage.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var applicationContext: WebApplicationContext

    /** MockMvc 懒初始化（Sa-Token 上下文过滤器显式挂载，与 AdminApiContractTest 一致） */
    private val mockMvc: MockMvc by lazy {
        val builder = MockMvcBuilders.webAppContextSetup(applicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(
            SaFirewallCheckFilterForJakartaServlet(),
            SaTokenContextFilterForJakartaServlet(),
        )
        builder.build()
    }

    private val token: String by lazy { login() }

    // ---------- 工具 ----------

    private fun login(
        username: String = "admin",
        password: String = "admin123",
    ): String =
        post(
            "/api/v1/auth/login",
            body = """{"username":"$username","password":"$password"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
            .response
            .contentAsString
            .let {
                DslJson.mapper
                    .readTree(it)
                    .get("token")
                    .asText()
            }

    private fun get(
        path: String,
        token: String? = null,
    ) = mockMvc.perform(MockMvcRequestBuilders.get(path).apply { token?.let { header("Authorization", it) } })

    private fun post(
        path: String,
        token: String? = null,
        body: String? = null,
    ) = mockMvc
        .perform(
            MockMvcRequestBuilders.post(path).apply {
                token?.let { header("Authorization", it) }
                body?.let {
                    contentType("application/json")
                    content(it)
                }
            },
        )

    private fun put(
        path: String,
        token: String? = null,
        body: String? = null,
    ) = mockMvc
        .perform(
            MockMvcRequestBuilders.put(path).apply {
                token?.let { header("Authorization", it) }
                body?.let {
                    contentType("application/json")
                    content(it)
                }
            },
        )

    private fun delete(
        path: String,
        token: String? = null,
    ) = mockMvc.perform(MockMvcRequestBuilders.delete(path).apply { token?.let { header("Authorization", it) } })

    /** 播种 VIEWER 角色用户（仅查看权限） */
    private fun seedUser(
        username: String,
        password: String,
        roleCode: String = "VIEWER",
    ) {
        transaction {
            val roleId =
                SysRolesTable
                    .select(SysRolesTable.id)
                    .where { SysRolesTable.roleCode eq roleCode }
                    .single()[SysRolesTable.id]
            val userId =
                SysUsersTable.insert { statement ->
                    statement[SysUsersTable.username] = username
                    statement[SysUsersTable.password] = BCryptPasswordEncoder().encode(password)!!
                    statement[SysUsersTable.nickname] = "测试用户-$username"
                    statement[SysUsersTable.status] = "ACTIVE"
                    statement[SysUsersTable.createdAt] = Instant.now()
                } get SysUsersTable.id
            SysUserRolesTable.insert { statement ->
                statement[SysUserRolesTable.userId] = userId
                statement[SysUserRolesTable.roleId] = roleId
            }
        }
    }

    /** 合法流程图（start → ruleset → end） */
    private fun graph(
        endAction: String = "PASS",
        rulesetKey: String = "rule_flow",
    ): String =
        """{"nodes":[
            {"id":"start-1","type":"start","position":{"x":0,"y":0},"data":{"label":"开始","nodeType":"start"}},
            {"id":"ruleset-1","type":"ruleset","position":{"x":100,"y":0},"data":{"label":"规则集","nodeType":"ruleset","ruleKeys":["$rulesetKey"]}},
            {"id":"end-1","type":"end","position":{"x":200,"y":0},"data":{"label":"结束","nodeType":"end","defaultAction":"$endAction","defaultReason":"默认"}}
        ],"edges":[
            {"id":"e1","source":"start-1","target":"ruleset-1"},
            {"id":"e2","source":"ruleset-1","target":"end-1"}
        ]}"""

    /** 非法流程图（两个 start，ERROR 级） */
    private val badGraph =
        """{"nodes":[
            {"id":"start-1","type":"start","position":{"x":0,"y":0},"data":{"label":"开始","nodeType":"start"}},
            {"id":"start-2","type":"start","position":{"x":50,"y":0},"data":{"label":"开始2","nodeType":"start"}}
        ],"edges":[]}"""

    private fun json(raw: String): String = DslJson.mapper.writeValueAsString(raw)

    // ---------- 决策流管理全链路 ----------

    private val flowKey = "peripheral_flow"

    @Test
    @Order(10)
    @DisplayName("决策流：创建 → 列表/查询/详情 → 元数据更新 → 图变更推进版本 → 草稿/发布/回滚 → 启停/软删")
    fun decisionFlowLifecycle() {
        // 1. 创建（响应字段与前端 types/decisionFlow.ts 的 DecisionFlow 对齐）
        post(
            "/api/v1/decision-flows",
            token,
            """{"flowKey":"$flowKey","flowName":"契约流程","flowDescription":"阶段2c",
                "flowGraph":${json(graph())}}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$.flowKey").value(flowKey))
            .andExpect(MockMvcResultMatchers.jsonPath("$.flowName").value("契约流程"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("DRAFT"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.enabled").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.optLockVersion").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeVersion").value(Matchers.nullValue()))
            .andExpect(MockMvcResultMatchers.jsonPath("$.createdBy").value("system"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.createdAt").isNotEmpty)

        // 2. 非法流程图 → 400 旧契约错误结构
        post(
            "/api/v1/decision-flows",
            token,
            """{"flowKey":"${flowKey}_bad","flowName":"坏图","flowGraph":${json(badGraph)}}""",
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(400))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value(Matchers.startsWith("流程图数据非法: ")))
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Bad Request"))

        // 3. 列表分页（NameListPage 下拉消费 content[].flowKey/flowName）
        get("/api/v1/decision-flows?page=0&size=200", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.content").isArray)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalPages").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.number").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(200))
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].flowKey").value(flowKey))

        // 4. 多条件查询（status/keyword/enabled）
        post("/api/v1/decision-flows/query?page=0&size=20", token, """{"status":"DRAFT","keyword":"$flowKey"}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
        post("/api/v1/decision-flows/query?page=0&size=20", token, """{"keyword":"不存在的key"}""")
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(0))

        // 5. 详情与未知详情
        get("/api/v1/decision-flows/$flowKey", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.flowDescription").value("阶段2c"))
        get("/api/v1/decision-flows/ghost", token)
            .andExpect(MockMvcResultMatchers.status().isNotFound)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("实体不存在: DecisionFlow key=ghost"))

        // 6. 仅元数据更新（版本不变）
        put("/api/v1/decision-flows/$flowKey", token, """{"flowName":"契约流程V2"}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.flowName").value("契约流程V2"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(1))

        // 7. 图变更 → 推进版本（v2 ACTIVE + 指针推进）
        put(
            "/api/v1/decision-flows/$flowKey",
            token,
            """{"flowGraph":${json(graph(endAction = "REJECT"))},"changeReason":"收紧出口"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeVersion").value(2))
        get("/api/v1/decision-flows/$flowKey/versions", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].version").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].flowId").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].isRollback").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].version").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].status").value("ARCHIVED"))

        // 8. 单版本详情 + 未知版本 404 空体（照旧）
        get("/api/v1/decision-flows/$flowKey/versions/2", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(2))
        get("/api/v1/decision-flows/$flowKey/versions/99", token)
            .andExpect(MockMvcResultMatchers.status().isNotFound)

        // 9. 新建草稿版本（DRAFT，不改变生效指针）
        post(
            "/api/v1/decision-flows/$flowKey/versions",
            token,
            """{"flowGraph":${json(graph(endAction = "MANUAL_REVIEW", rulesetKey = "rule_v3"))},"changeReason":"草稿v3"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(3))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("DRAFT"))
        get("/api/v1/decision-flows/$flowKey", token)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(3))
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeVersion").value(2))

        // 10. 发布守卫（ACTIVE 版本不可再发布）→ 发布草稿 v3
        post("/api/v1/decision-flows/$flowKey/versions/2/publish", token)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.message")
                    .value("只有草稿或灰度中的版本才能发布，当前状态: ACTIVE"),
            )
        post("/api/v1/decision-flows/$flowKey/versions/3/publish", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"))
        get("/api/v1/decision-flows/$flowKey", token)
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeVersion").value(3))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(3))
        get("/api/v1/decision-flows/$flowKey/versions", token)
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].version").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].status").value("ARCHIVED"))

        // 11. 回滚到 v1（更高版本号副本 v4 + 立即生效）
        post(
            "/api/v1/decision-flows/$flowKey/versions/rollback",
            token,
            """{"targetVersion":1}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(4))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.isRollback").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.rollbackFromVersion").value(1))
        get("/api/v1/decision-flows/$flowKey", token)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(4))
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeVersion").value(4))

        // 12. 启停与软删（列表不过滤 DELETED——照旧；详情可见）
        post("/api/v1/decision-flows/$flowKey/disable", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.enabled").value(false))
        post("/api/v1/decision-flows/$flowKey/enable", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.enabled").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"))
        delete("/api/v1/decision-flows/$flowKey", token).andExpect(MockMvcResultMatchers.status().isOk)
        // 软删后读取路径不可见（findMain 排除 DELETED）：详情返回 404；同名 Key 可重建
        get("/api/v1/decision-flows/$flowKey", token)
            .andExpect(MockMvcResultMatchers.status().isNotFound)
        post("/api/v1/decision-flows/query?page=0&size=20", token, """{"status":"DELETED"}""")
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
    }

    @Test
    @Order(11)
    @DisplayName("灰度联动兼容：决策流版本可走灰度全量切换（complete 推进语义与本批发布同构）")
    fun grayscaleIntegrationCompatible() {
        // 独立流程：创建 v1 → 发布 v2（生效指针落地）→ 草稿 v3 → 灰度（v3 vs 现行 v2）→ 启动 → 发布
        val key = "grayscale_flow"
        post(
            "/api/v1/decision-flows",
            token,
            """{"flowKey":"$key","flowName":"灰度流程","flowGraph":${json(graph(rulesetKey = "rule_gs"))}}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
        post(
            "/api/v1/decision-flows/$key/versions",
            token,
            """{"flowGraph":${json(graph(endAction = "REJECT", rulesetKey = "rule_gs2"))}}""",
        ).andExpect(MockMvcResultMatchers.jsonPath("$.version").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("DRAFT"))
        // 先发布 v2，让生效指针落地（否则 currentVersion 兜底与灰度版本同号，灰度创建按领域不变式拒绝）
        post("/api/v1/decision-flows/$key/versions/2/publish", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"))
        post(
            "/api/v1/decision-flows/$key/versions",
            token,
            """{"flowGraph":${json(graph(endAction = "MANUAL_REVIEW", rulesetKey = "rule_gs3"))},"changeReason":"v3"}""",
        ).andExpect(MockMvcResultMatchers.jsonPath("$.version").value(3))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("DRAFT"))

        val created =
            post(
                "/api/v1/grayscale",
                token,
                """{"targetType":"DECISION_FLOW","targetKey":"$key","grayscaleVersion":3,
                    "grayscalePercentage":10,"strategyType":"PERCENTAGE"}""",
            ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.jsonPath("$.targetType").value("DECISION_FLOW"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.currentVersion").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.grayscaleVersion").value(3))
                .andReturn()
                .response
                .contentAsString
        val configId =
            DslJson.mapper
                .readTree(created)
                .get("id")
                .asLong()

        put("/api/v1/grayscale/$configId/start", token)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("RUNNING"))
        // 灰度启动把 v3 推进为 CANARY（2b 灰度服务语义），本批发布接口对 CANARY 也可发布
        post("/api/v1/decision-flows/$key/versions/3/publish", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"))
        get("/api/v1/decision-flows/$key", token)
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeVersion").value(3))
    }

    // ---------- 黑白名单 ----------

    @Test
    @Order(20)
    @DisplayName("名单：创建（listKey 缺省 GLOBAL）→ 重复 400 → 过滤列表 → list-keys → 详情/404 → 删除 204")
    fun nameListCrud() {
        post(
            "/api/v1/name-list",
            token,
            """{"listType":"BLACK","keyType":"IP","keyValue":"10.0.0.1","reason":"欺诈","source":"人工"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$.listKey").value("GLOBAL"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.listType").value("BLACK"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.expiredAt").value(Matchers.nullValue()))
            .andExpect(MockMvcResultMatchers.jsonPath("$.createdBy").value("system"))

        // 流级隔离键 + 过期时间
        post(
            "/api/v1/name-list",
            token,
            """{"listType":"WHITE","listKey":"$flowKey","keyType":"DEVICE_ID","keyValue":"dev-1",
                "expiredAt":"2030-01-01T10:30:00"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.listKey").value(flowKey))
            .andExpect(MockMvcResultMatchers.jsonPath("$.expiredAt").isNotEmpty)

        // 重复业务键 → 400
        post(
            "/api/v1/name-list",
            token,
            """{"listType":"BLACK","keyType":"IP","keyValue":"10.0.0.1"}""",
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value(Matchers.startsWith("名单条目已存在")))

        // 过滤列表（前端 NameListPage 的三个过滤参数 + page/size）
        get("/api/v1/name-list?page=0&size=10", token)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].keyValue").value("10.0.0.1"))
        get("/api/v1/name-list?listType=WHITE&keyType=DEVICE_ID&page=0&size=10", token)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
        get("/api/v1/name-list?listKey=$flowKey&page=0&size=10", token)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))

        // list-keys（前端 NodeConfigPanel 名单节点下拉）
        get("/api/v1/name-list/list-keys", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0]").value("GLOBAL"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1]").value(flowKey))
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))

        // 详情与 404
        get("/api/v1/name-list/1", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.keyValue").value("10.0.0.1"))
        get("/api/v1/name-list/999", token).andExpect(MockMvcResultMatchers.status().isNotFound)

        // 删除 → 204 空体（照旧），再查 404
        delete("/api/v1/name-list/1", token).andExpect(MockMvcResultMatchers.status().isNoContent)
        get("/api/v1/name-list/1", token).andExpect(MockMvcResultMatchers.status().isNotFound)

        // Bean Validation：缺 listType → 400 字段级消息（照旧）
        post("/api/v1/name-list", token, """{"keyType":"IP","keyValue":"1.1.1.1"}""")
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("listType: 名单类型不能为空"))
    }

    // ---------- 审计日志 ----------

    @Test
    @Order(30)
    @DisplayName("审计：播种日志 → 实体历史 → 操作人活动 → 分页查询（权限 api:system:role:view）")
    fun auditQueries() {
        // 播种（写入侧由后续批次统一规划，此处直插验证只读查询契约）
        transaction {
            AuditLogsTable.insert { statement ->
                statement[entityType] = "DECISION_FLOW"
                statement[entityId] = flowKey
                statement[operation] = "FLOW_CREATE"
                statement[operationDetail] = """{"flowKey":"$flowKey"}"""
                statement[operator] = "alice"
                statement[operationTime] = Instant.parse("2026-01-10T00:01:00Z")
                statement[status] = "SUCCESS"
            }
            AuditLogsTable.insert { statement ->
                statement[entityType] = "DECISION_FLOW"
                statement[entityId] = flowKey
                statement[operation] = "FLOW_UPDATE"
                statement[operator] = "bob"
                statement[operationTime] = Instant.parse("2026-01-10T00:02:00Z")
                statement[status] = "SUCCESS"
            }
            AuditLogsTable.insert { statement ->
                statement[entityType] = "RULE"
                statement[entityId] = "some_rule"
                statement[operation] = "RULE_CREATE"
                statement[operator] = "alice"
                statement[operationTime] = Instant.parse("2026-01-10T00:03:00Z")
                statement[status] = "FAILED"
                statement[errorMessage] = "脚本校验失败"
            }
        }

        // 实体历史（降序；前端 system.ts AuditLogDTO 字段）
        get("/api/v1/audit/logs/DECISION_FLOW/$flowKey", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].operation").value("FLOW_UPDATE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].operationTime").isNotEmpty)
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].operation").value("FLOW_CREATE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].operationDetail").isNotEmpty)

        // 操作人活动（默认回看 7 天不含播种时间 → 显式窗口）
        get(
            "/api/v1/audit/logs/operator/alice?start=2026-01-09T00:00:00&end=2026-01-11T00:00:00",
            token,
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))

        // 分页综合查询（operator 模糊）
        get("/api/v1/audit/logs?operator=ali&page=0&size=20", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].operator").isNotEmpty)
        get("/api/v1/audit/logs?entityType=RULE&operation=RULE_CREATE&page=0&size=20", token)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].status").value("FAILED"))
    }

    // ---------- 环境管理 ----------

    @Test
    @Order(40)
    @DisplayName("环境：V7 种子列表 → 详情/404 → 环境下规则 → 克隆（空/跳过/覆盖）→ 未知环境 400")
    fun environmentLifecycle() {
        // V7 种子的三个环境（前端 EnvironmentPage 卡片消费 id/name/type/description）
        get("/api/v1/environments", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(3))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value("DEV"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("DEV"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].name").value("STAGING"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[2].name").value("PRODUCTION"))

        // 详情与 404 语义（照旧 IllegalArgumentException → 400）
        get("/api/v1/environments/1", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("DEV"))
        get("/api/v1/environments/999", token)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("环境不存在: 999"))

        // 环境下规则（播种两条 DEV 规则；rules.rule_key 全局唯一，跨环境同键不可复制）
        transaction {
            RulesTable.insert { statement ->
                statement[ruleKey] = "env_dev_rule"
                statement[ruleName] = "DEV 环境规则"
                statement[ruleDescription] = null
                statement[groovyScript] = "return 'PASS'"
                statement[version] = 1
                statement[enabled] = true
                statement[deleted] = false
                statement[createdBy] = "tester"
                statement[createdAt] = Instant.now()
                statement[environmentId] = 1L
            }
            RulesTable.insert { statement ->
                statement[ruleKey] = "env_dev_rule_2"
                statement[ruleName] = "DEV 环境规则2"
                statement[ruleDescription] = null
                statement[groovyScript] = "return 'REVIEW'"
                statement[version] = 1
                statement[enabled] = true
                statement[deleted] = false
                statement[createdBy] = "tester"
                statement[createdAt] = Instant.now()
                statement[environmentId] = 1L
            }
        }
        get("/api/v1/environments/1/rules", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].ruleKey").value("env_dev_rule"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].groovyScript").value("return 'PASS'"))

        // 克隆 DEV → STAGING：rule_key 全局唯一 → 源行自身占用键，跨环境复制计为跳过
        // （旧实现在此场景直接违反唯一约束 500；现实数据下规则不挂环境，新旧一致为 0/0）
        post("/api/v1/environments/DEV/clone/STAGING", token, """{"overwrite":false}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.clonedCount").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.skippedCount").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("克隆完成: 复制 0 条规则, 跳过 2 条"))

        // 空环境克隆：PRODUCTION 无规则 → 0/0（与旧实现现实数据下的行为一致）
        post("/api/v1/environments/PRODUCTION/clone/STAGING", token, null)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.clonedCount").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.skippedCount").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("克隆完成: 复制 0 条规则, 跳过 0 条"))

        // 未知环境 → 400 旧契约消息
        post("/api/v1/environments/GHOST/clone/DEV", token, null)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("环境不存在: GHOST"))
    }

    // ---------- 权限 ----------

    @Test
    @Order(50)
    @DisplayName("权限：未登录 401；VIEWER 可读决策流/版本/名单，写操作与审计分页 403")
    fun permissionMatrix() {
        seedUser("peripheral_viewer", "viewer-pass")
        val viewer = login("peripheral_viewer", "viewer-pass")

        // 未登录 → 401 旧契约结构
        get("/api/v1/decision-flows")
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("未登录或登录已过期"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Unauthorized"))
        get("/api/v1/name-list", token = null)
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        get("/api/v1/audit/logs")
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)

        // VIEWER：可读（决策流列表/版本/名单/实体审计历史）
        get("/api/v1/decision-flows?page=0&size=10", viewer).andExpect(MockMvcResultMatchers.status().isOk)
        get("/api/v1/decision-flows/$flowKey/versions", viewer).andExpect(MockMvcResultMatchers.status().isOk)
        get("/api/v1/name-list?page=0&size=10", viewer).andExpect(MockMvcResultMatchers.status().isOk)
        get("/api/v1/name-list/list-keys", viewer).andExpect(MockMvcResultMatchers.status().isOk)
        get("/api/v1/audit/logs/DECISION_FLOW/x", viewer).andExpect(MockMvcResultMatchers.status().isOk)
        get("/api/v1/audit/logs/operator/alice", viewer).andExpect(MockMvcResultMatchers.status().isOk)
        // 环境接口仅要求登录
        get("/api/v1/environments", viewer).andExpect(MockMvcResultMatchers.status().isOk)

        // VIEWER：写操作 403（消息带权限码）
        post(
            "/api/v1/decision-flows",
            viewer,
            """{"flowKey":"nope","flowName":"越权","flowGraph":${json(graph())}}""",
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(403))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("无操作权限: api:decision-flows:create"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Forbidden"))
        post("/api/v1/decision-flows/$flowKey/versions/rollback", viewer, """{"targetVersion":1}""")
            .andExpect(MockMvcResultMatchers.status().isForbidden)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("无操作权限: api:decision-flows:update"))
        post("/api/v1/name-list", viewer, """{"listType":"BLACK","keyType":"IP","keyValue":"1.1.1.1"}""")
            .andExpect(MockMvcResultMatchers.status().isForbidden)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("无操作权限: api:name-list:manage"))
        delete("/api/v1/name-list/1", viewer)
            .andExpect(MockMvcResultMatchers.status().isForbidden)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("无操作权限: api:name-list:delete"))
        // 审计分页要求 api:system:role:view（VIEWER 无此权限）
        get("/api/v1/audit/logs?page=0&size=20", viewer)
            .andExpect(MockMvcResultMatchers.status().isForbidden)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("无操作权限: api:system:role:view"))
    }
}
