package com.eatmoreduck.ruleengine.decision

import cn.dev33.satoken.filter.SaFirewallCheckFilterForJakartaServlet
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
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

/**
 * 决策端点认证契约（auth-enabled=true 完整拦截链）：未登录访问受保护端点 →
 * 401 旧契约三字段结构（{code:401, message:"未登录或登录已过期", error:"Unauthorized"}）。
 *
 * 登录会话建立在 admin-api（部署物 2）；阶段 5 起会话经 Redis 共享（admin 颁发的 token
 * 在决策侧有效，Redis 不可用时回退各自内存），本类只覆盖未登录/拦截面，正向 token 流由
 * [DecisionApiContractTest]（auth-enabled=false）承担业务链路。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@DisplayName("decision-api 认证契约：未登录 401 结构")
class DecisionApiAuthContractTest {
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
            // JSONB 列文本写入依赖 stringtype=unspecified（见 ExecutionLogTables 注释）
            val url = postgres.jdbcUrl
            registry.add(
                "ruleengine.storage.url",
            ) { if (url.contains("?")) "$url&stringtype=unspecified" else "$url?stringtype=unspecified" }
            registry.add("ruleengine.storage.username", postgres::getUsername)
            registry.add("ruleengine.storage.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var applicationContext: WebApplicationContext

    private val mockMvc: MockMvc by lazy {
        // Sa-Token 的请求上下文由 Servlet 过滤器建立，MockMvc 需显式挂载（防火墙在前，与生产注册顺序一致）
        val builder = MockMvcBuilders.webAppContextSetup(applicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(
            SaFirewallCheckFilterForJakartaServlet(),
            SaTokenContextFilterForJakartaServlet(),
        )
        builder.build()
    }

    @Test
    @DisplayName("未登录访问决策端点返回 401 旧契约结构（健康端点豁免）")
    fun unauthenticatedHasLegacyErrorShape() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"ruleId":"r","script":"return 'PASS'"}"""),
            ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("未登录或登录已过期"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Unauthorized"))

        mockMvc
            .perform(MockMvcRequestBuilders.post("/api/v1/decide/some_rule").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)

        mockMvc
            .perform(MockMvcRequestBuilders.get("/api/v1/decide/async/some-id"))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)

        // 健康端点在白名单内
        mockMvc
            .perform(MockMvcRequestBuilders.get("/api/v1/health"))
            .andExpect(MockMvcResultMatchers.status().isOk)
    }
}
