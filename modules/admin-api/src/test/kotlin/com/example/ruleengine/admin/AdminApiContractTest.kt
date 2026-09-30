package com.example.ruleengine.admin

import cn.dev33.satoken.filter.SaFirewallCheckFilterForJakartaServlet
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet
import com.example.ruleengine.admin.auth.SysRolesTable
import com.example.ruleengine.admin.auth.SysUserRolesTable
import com.example.ruleengine.admin.auth.SysUsersTable
import com.example.ruleengine.dsl.DslJson
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
 * admin-api 全链路契约测试：Testcontainers PG16（真实 PostgreSQL + Flyway V1..V25 迁移 +
 * V15/V16/V17/V25 权限种子数据）+ 完整 Spring 上下文（Sa-Token 拦截链）+ MockMvc。
 *
 * 断言口径为前端消费方（frontend/src/api 与 frontend/src/types 下的类型定义）的字段名与解析逻辑，
 * 无 Docker 的环境自动跳过整个类（等价 @Disabled，构建不挂）。
 *
 * 说明：Sa-Token × Boot 4 的上下文加载探针已并入本类的认证用例
 * （登录颁发 token → token 可访问受保护接口 → 登出后失效 → 401/403 契约结构）。
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@SpringBootTest
@DisplayName("admin-api 契约：登录认证 + 规则 CRUD + 版本 + 灰度 + 特征目录")
class AdminApiContractTest {
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

    /**
     * MockMvc 懒初始化（依赖注入完成后构建）。
     *
     * 注意：Sa-Token 的请求上下文由 Servlet 过滤器建立
     * （SaTokenContextFilterForJakartaServlet，生产环境经 Boot 自动装配的 FilterRegistrationBean 生效），
     * MockMvc 不会自动应用上下文中的 Filter Bean，须显式挂载——防火墙过滤器在前（与生产注册顺序一致）。
     */
    private val mockMvc: MockMvc by lazy {
        val builder = MockMvcBuilders.webAppContextSetup(applicationContext)
        // 显式指定自类型参数（spring-test 的 addFilters 返回 <T : B> T，Kotlin 无法自行推断）
        builder.addFilters<DefaultMockMvcBuilder>(
            SaFirewallCheckFilterForJakartaServlet(),
            SaTokenContextFilterForJakartaServlet(),
        )
        builder.build()
    }

    /** 管理员 token（懒加载：首个业务用例时登录） */
    private val flowToken: String by lazy { login() }

    // ---------- 工具 ----------

    private fun login(
        username: String = "admin",
        password: String = "admin123",
    ): String {
        val body =
            post(
                "/api/v1/auth/login",
                body = """{"username":"$username","password":"$password"}""",
            ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .response
                .contentAsString
        return DslJson.mapper
            .readTree(body)
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

    /** 播种一个非管理员用户（绑定指定角色，默认 VIEWER 仅查看权限） */
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
                    // Spring Security 7 的 encode 返回值标注可空（jsr305 strict 下为 String?），实际恒非空
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

    // ---------- 认证（含 Sa-Token × Boot 4 探针） ----------

    @Test
    @Order(10)
    @DisplayName("登录颁发 token，token 可访问受保护接口，登出后失效")
    fun authLifecycle() {
        val body =
            post("/api/v1/auth/login", body = """{"username":"admin","password":"admin123"}""")
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.jsonPath("$.token").isNotEmpty)
                .andExpect(MockMvcResultMatchers.jsonPath("$.username").value("admin"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.nickname").value("系统管理员"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.roles[0]").value("SUPER_ADMIN"))
                .andReturn()
                .response
                .contentAsString
        val token =
            DslJson.mapper
                .readTree(body)
                .get("token")
                .asText()

        // 当前用户信息（字段与前端 UserInfo 对齐）
        get("/api/v1/auth/me", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$.username").value("admin"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.roles[0]").value("SUPER_ADMIN"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.permissions").value(Matchers.hasItem("api:rules:create")))
            .andExpect(MockMvcResultMatchers.jsonPath("$.permissions").value(Matchers.hasItem("api:feature-catalog:manage")))

        // 登录状态检查
        get("/api/v1/auth/check", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.loggedIn").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.userId").isNumber)

        // 登出后旧 token 失效 → 401 旧契约结构
        post("/api/v1/auth/logout", token).andExpect(MockMvcResultMatchers.status().isOk)
        get("/api/v1/auth/me", token)
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("未登录或登录已过期"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Unauthorized"))
    }

    @Test
    @Order(11)
    @DisplayName("未登录访问受保护接口返回 401 旧契约结构")
    fun unauthorizedHasLegacyErrorShape() {
        get("/api/v1/rules")
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("未登录或登录已过期"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Unauthorized"))
    }

    @Test
    @Order(12)
    @DisplayName("错误密码返回 400，连续失败 5 次后触发锁定消息")
    fun loginFailureAndLockout() {
        seedUser("lockme", "right-pass")
        repeat(5) {
            post("/api/v1/auth/login", body = """{"username":"lockme","password":"wrong"}""")
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("用户名或密码错误"))
        }
        // 第 6 次：即使密码正确也被锁定
        post("/api/v1/auth/login", body = """{"username":"lockme","password":"right-pass"}""")
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("账号已被锁定，请 30 分钟后重试"))
    }

    @Test
    @Order(13)
    @DisplayName("请求参数校验失败返回 400 字段级消息")
    fun beanValidationMessage() {
        post("/api/v1/auth/login", body = """{"username":"","password":""}""")
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(400))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("password: 密码不能为空; username: 用户名不能为空"))
    }

    // ---------- 规则 CRUD + 版本 + 灰度 全链路 ----------

    private val ruleKey = "contract_flow_rule"

    @Test
    @Order(20)
    @DisplayName("创建规则 → 元数据更新 → 新版本 → 灰度全生命周期 → 回滚 → 删除")
    fun fullBusinessFlow() {
        val scriptV1 = "def amt = features.order_amount\nreturn amt > 1000 ? 'REJECT' : 'PASS'"

        // 1. 创建规则（响应字段与前端 types/rule.ts 的 Rule 对齐）
        post(
            "/api/v1/rules",
            flowToken,
            """{"ruleKey":"$ruleKey","ruleName":"契约规则","ruleDescription":"全链路","groovyScript":${json(scriptV1)}}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$.ruleKey").value(ruleKey))
            .andExpect(MockMvcResultMatchers.jsonPath("$.ruleName").value("契约规则"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.enabled").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.deleted").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.optLockVersion").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeVersion").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.createdBy").value("system"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.createdAt").isNotEmpty)
            .andExpect(MockMvcResultMatchers.jsonPath("$.groovyScript").value(scriptV1))

        // 2. 重复创建 → 400 旧契约结构
        post(
            "/api/v1/rules",
            flowToken,
            """{"ruleKey":"$ruleKey","ruleName":"重复","groovyScript":${json(scriptV1)}}""",
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(400))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("规则Key已存在: $ruleKey"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Bad Request"))

        // 3. 危险脚本 → 400 校验链消息
        post(
            "/api/v1/rules",
            flowToken,
            """{"ruleKey":"${ruleKey}_bad","ruleName":"坏规则","groovyScript":"System.exit(1)"}""",
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.message")
                    .value(Matchers.startsWith("Groovy脚本语法错误: 安全审计失败")),
            )

        // 4. 非法 DSL JSON → 400
        post(
            "/api/v1/rules",
            flowToken,
            """{"ruleKey":"${ruleKey}_badjson","ruleName":"坏JSON","groovyScript":"{\"rules\": broken"}""",
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.message").value(Matchers.startsWith("Groovy脚本语法错误: ")),
            )

        // 5. 列表（分页五字段）与详情与多条件查询
        get("/api/v1/rules?page=0&size=20&keyword=$ruleKey", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.content").isArray)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalPages").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.number").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(20))
        get("/api/v1/rules/$ruleKey", flowToken).andExpect(MockMvcResultMatchers.status().isOk)
        post("/api/v1/rules/query?page=0&size=20", flowToken, """{"enabled":true,"keyword":"$ruleKey"}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))

        // 6. 仅元数据更新（版本号不变）
        put("/api/v1/rules/$ruleKey", flowToken, """{"ruleName":"契约规则V2","ruleDescription":"改名"}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.ruleName").value("契约规则V2"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(1))

        // 7. 新版本（DRAFT，不改变生效版本）
        val scriptV2 = "def amt = features.order_amount\nreturn amt > 500 ? 'REJECT' : 'PASS'"
        post(
            "/api/v1/rules/$ruleKey/versions",
            flowToken,
            """{"groovyScript":${json(scriptV2)},"changeReason":"放宽阈值"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("DRAFT"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.changedBy").value("system"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.groovyScript").value(scriptV2))
        get("/api/v1/rules/$ruleKey", flowToken)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeVersion").value(1))

        // 8. 版本列表（降序 + 状态字段）、单版本、对比
        get("/api/v1/rules/$ruleKey/versions", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].version").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].status").value("DRAFT"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].isRollback").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].version").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].status").value("ACTIVE"))
        get("/api/v1/rules/$ruleKey/versions/2", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(2))
        get("/api/v1/rules/$ruleKey/versions/compare?version1=1&version2=2", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.ruleKey").value(ruleKey))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version1").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.script1").value(scriptV1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.diff").isNotEmpty)

        // 9. 创建灰度（v2 vs 现行 v1）
        val grayscaleBody =
            post(
                "/api/v1/grayscale",
                flowToken,
                """{"ruleKey":"$ruleKey","targetType":"RULE","targetKey":"$ruleKey","grayscaleVersion":2,
                    "grayscalePercentage":50,"strategyType":"PERCENTAGE","dualRunEnabled":false,"description":"契约灰度"}""",
            ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").isNumber)
                .andExpect(MockMvcResultMatchers.jsonPath("$.ruleKey").value(ruleKey))
                .andExpect(MockMvcResultMatchers.jsonPath("$.targetType").value("RULE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.targetKey").value(ruleKey))
                .andExpect(MockMvcResultMatchers.jsonPath("$.currentVersion").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.grayscaleVersion").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.grayscalePercentage").value(50))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("DRAFT"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.statusDescription").value("草稿"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.strategyType").value("PERCENTAGE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.createdBy").value("system"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.createdAt").isNotEmpty)
                .andReturn()
                .response
                .contentAsString
        val grayscaleId =
            DslJson.mapper
                .readTree(grayscaleBody)
                .get("id")
                .asLong()

        // 10. 启动灰度 → 灰度版本真正写入 CANARY（阶段 1 修正）
        put("/api/v1/grayscale/$grayscaleId/start", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("RUNNING"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.statusDescription").value("运行中"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.startedAt").isNotEmpty)
        get("/api/v1/rules/$ruleKey/versions", flowToken)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].version").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].status").value("CANARY"))

        // 11. 分流状态查询（列表过滤 + 规则维度）
        get("/api/v1/grayscale?status=RUNNING&ruleKey=$ruleKey", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
        get("/api/v1/grayscale/rule/$ruleKey", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(grayscaleId))

        // 12. 对比报告（前端 GrayscaleReport 形状）
        get("/api/v1/grayscale/$grayscaleId/report", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.configId").value(grayscaleId))
            .andExpect(MockMvcResultMatchers.jsonPath("$.ruleKey").value(ruleKey))
            .andExpect(MockMvcResultMatchers.jsonPath("$.currentVersion").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.grayscaleVersion").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.currentVersionMetrics.version").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.currentVersionMetrics.executionCount").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.currentVersionMetrics.hitRate").value(0.0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.grayscaleVersionMetrics.errorRate").value(0.0))

        // 13. 全量切换 → 版本发布（CANARY→ACTIVE）+ 旧 ACTIVE 归档 + 主表推进
        put("/api/v1/grayscale/$grayscaleId/complete", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("COMPLETED"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.statusDescription").value("已完成"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.grayscalePercentage").value(100))
            .andExpect(MockMvcResultMatchers.jsonPath("$.completedAt").isNotEmpty)
        get("/api/v1/rules/$ruleKey", flowToken)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeVersion").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.groovyScript").value(scriptV2))
        get("/api/v1/rules/$ruleKey/versions", flowToken)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].version").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].status").value("ARCHIVED"))

        // 14. 终态守卫（旧契约消息）
        put("/api/v1/grayscale/$grayscaleId/start", flowToken)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.message")
                    .value("只有草稿或已暂停状态的灰度配置才能启动，当前状态: 已完成"),
            )
        put("/api/v1/grayscale/$grayscaleId/rollback", flowToken)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.message")
                    .value("已完成或已回滚的灰度配置不能再次回滚，当前状态: 已完成"),
            )

        // 15. 回滚到 v1（更高版本号落地 + 立即发布生效，rollbackFromVersion = 目标版本）
        post(
            "/api/v1/rules/$ruleKey/versions/1/rollback",
            flowToken,
            """{"targetVersion":1,"reason":"契约回滚"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(3))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.isRollback").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.rollbackFromVersion").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.groovyScript").value(scriptV1))
        get("/api/v1/rules/$ruleKey", flowToken)
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(3))
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeVersion").value(3))
            .andExpect(MockMvcResultMatchers.jsonPath("$.groovyScript").value(scriptV1))

        // 16. 启停与软删除（列表排除已删除，详情仍可见）
        post("/api/v1/rules/$ruleKey/disable", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.enabled").value(false))
        post("/api/v1/rules/$ruleKey/enable", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.enabled").value(true))
        delete("/api/v1/rules/$ruleKey", flowToken).andExpect(MockMvcResultMatchers.status().isOk)
        get("/api/v1/rules/$ruleKey", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.deleted").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.enabled").value(false))
        get("/api/v1/rules?page=0&size=20&keyword=$ruleKey", flowToken)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(0))
        delete("/api/v1/rules/$ruleKey", flowToken)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("规则正在使用中，不能删除: $ruleKey"))
    }

    @Test
    @Order(21)
    @DisplayName("暂停与回滚灰度：DRAFT→RUNNING→PAUSED→ROLLED_BACK")
    fun pauseAndRollbackFlow() {
        val script = "return 'PASS'"
        post(
            "/api/v1/rules",
            flowToken,
            """{"ruleKey":"contract_pause_rule","ruleName":"暂停规则","groovyScript":${json(script)}}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
        post(
            "/api/v1/rules/contract_pause_rule/versions",
            flowToken,
            """{"groovyScript":"return 'REJECT'"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)

        val created =
            post(
                "/api/v1/grayscale",
                flowToken,
                """{"ruleKey":"contract_pause_rule","grayscaleVersion":2,"grayscalePercentage":10}""",
            ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .response
                .contentAsString
        val id =
            DslJson.mapper
                .readTree(created)
                .get("id")
                .asLong()

        put("/api/v1/grayscale/$id/start", flowToken)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("RUNNING"))
        put("/api/v1/grayscale/$id/pause", flowToken)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("PAUSED"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.statusDescription").value("已暂停"))
        put("/api/v1/grayscale/$id/rollback", flowToken)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ROLLED_BACK"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.statusDescription").value("已回滚"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.grayscalePercentage").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.completedAt").isNotEmpty)
    }

    @Test
    @Order(22)
    @DisplayName("脚本验证端点：合法脚本通过，危险脚本给出旧契约结构")
    fun validateEndpoint() {
        post("/api/v1/rules/validate", flowToken, """{"groovyScript":"return 'PASS'"}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.valid").value(true))
        post("/api/v1/rules/validate", flowToken, """{"groovyScript":"Runtime.getRuntime().exec('ls')"}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.valid").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Groovy脚本语法错误"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorDetails").isNotEmpty)
    }

    // ---------- 权限 ----------

    @Test
    @Order(30)
    @DisplayName("无权限用户访问写接口返回 403 旧契约结构")
    fun forbiddenForViewer() {
        seedUser("contract_viewer", "viewer-pass")
        val token = login("contract_viewer", "viewer-pass")
        // VIEWER 具备 api:rules:view，可读
        get("/api/v1/rules", token).andExpect(MockMvcResultMatchers.status().isOk)
        // 但不可创建
        post(
            "/api/v1/rules",
            token,
            """{"ruleKey":"viewer_rule","ruleName":"越权","groovyScript":"return 'PASS'"}""",
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(403))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("无操作权限: api:rules:create"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Forbidden"))
    }

    // ---------- 特征目录 ----------

    @Test
    @Order(40)
    @DisplayName("特征目录 CRUD + 别名校验 + 引用查询 + 检索分页")
    fun featureCatalogFlow() {
        // 创建（响应字段与前端 types/featureCatalog.ts 的 FeatureDefinition 对齐）
        post(
            "/api/v1/features/catalog",
            flowToken,
            """{"code":"contract_amount","name":"订单金额","dataType":"NUMBER","sourceType":"INPUT",
                "exampleValue":"100","description":"测试特征","scope":"ORDER","aliases":["contract_amt"]}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("contract_amount"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.dataType").value("NUMBER"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.sensitivity").value("NORMAL"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.aliases[0]").value("contract_amt"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.createdAt").isNotEmpty)

        // 重复编码 → 400
        post(
            "/api/v1/features/catalog",
            flowToken,
            """{"code":"CONTRACT_AMOUNT","name":"重复","dataType":"NUMBER","sourceType":"INPUT"}""",
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("特征编码已存在: CONTRACT_AMOUNT"))

        // 别名与已有编码冲突 → 400
        post(
            "/api/v1/features/catalog",
            flowToken,
            """{"code":"other_feature","name":"其他","dataType":"STRING","sourceType":"INPUT","aliases":["contract_amount"]}""",
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("别名与已有特征编码冲突: contract_amount"))

        // 详情（大小写不敏感）与更新
        get("/api/v1/features/catalog/CONTRACT_AMOUNT", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("contract_amount"))
        put(
            "/api/v1/features/catalog/contract_amount",
            flowToken,
            """{"code":"contract_amount","name":"订单总金额","dataType":"NUMBER","sourceType":"INPUT",
                "aliases":["contract_amt","pay_amt"]}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("订单总金额"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.aliases.length()").value(2))

        // 批量校验：别名命中 + 未知字段
        post(
            "/api/v1/features/catalog/validate",
            flowToken,
            """{"items":[{"fieldName":"contract_amt"},{"fieldName":"ghost_field","operator":"GT","threshold":1}]}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.valid").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].found").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].matchedByAlias").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].canonicalCode").value("contract_amount"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.items[1].found").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.unknownFields[0]").value("ghost_field"))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.warnings").value(Matchers.hasItem("字段 ghost_field 未收录于特征字典")),
            )

        // 引用：规则脚本引用特征
        post(
            "/api/v1/rules",
            flowToken,
            """{"ruleKey":"feature_ref_rule","ruleName":"特征引用规则",
                "groovyScript":${json("def amt = features.contract_amount\nreturn 'PASS'")}}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
        get("/api/v1/features/catalog/contract_amount/references", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("rule"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value("特征引用规则"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].key").value("feature_ref_rule"))

        // 检索分页
        get("/api/v1/features/catalog?dataType=NUMBER&keyword=contract&page=0&size=20", flowToken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.content").isArray)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("contract_amount"))
    }

    // ---------- 私有工具 ----------

    /** 把字符串编码为 JSON 字符串字面量（内嵌到请求体中） */
    private fun json(raw: String): String = DslJson.mapper.writeValueAsString(raw)
}
