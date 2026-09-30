package com.example.ruleengine.storage

import com.example.ruleengine.storage.repository.DecisionFlowMain
import com.example.ruleengine.storage.repository.DecisionFlowRepository
import com.example.ruleengine.storage.repository.DecisionFlowVersion
import com.example.ruleengine.storage.repository.ExposedDecisionFlowRepository
import com.example.ruleengine.storage.table.DecisionFlowVersionsTable
import com.example.ruleengine.storage.table.DecisionFlowsTable
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 决策流仓储映射测试（H2 内存库，SchemaUtils 生成 DDL）。
 * Exposed v1 要求所有仓储调用处于事务上下文，用例内统一以 transaction { } 包裹；
 * 真实 PostgreSQL schema 核对由 PostgresStorageIntegrationTest 承担。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionFlowRepositoryTest {
    private val repository: DecisionFlowRepository = ExposedDecisionFlowRepository()

    @BeforeAll
    fun connectAndCreateSchema() {
        val dataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:mem:decision-flow-repo;DB_CLOSE_DELAY=-1")
            }
        Database.connect(dataSource)
        transaction {
            SchemaUtils.create(DecisionFlowsTable, DecisionFlowVersionsTable)
        }
    }

    private fun main(
        flowKey: String,
        version: Int = 1,
        activeVersion: Int? = 1,
        status: String = "ACTIVE",
    ) = DecisionFlowMain(
        id = null,
        flowKey = flowKey,
        flowName = "风控流程 $flowKey",
        flowDescription = "E2E 演练流程",
        flowGraph = """{"nodes":[],"edges":[]}""",
        version = version,
        activeVersion = activeVersion,
        status = status,
        createdBy = "admin",
        createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
        updatedBy = null,
        updatedAt = null,
        enabled = true,
        environmentId = null,
    )

    private fun version(
        flowKey: String,
        version: Int,
        flowId: Long,
        status: String = "DRAFT",
    ) = DecisionFlowVersion(
        id = null,
        flowId = flowId,
        flowKey = flowKey,
        version = version,
        flowGraph = """{"nodes":[],"edges":[]}""",
        changeReason = "第 $version 版",
        changedBy = "admin",
        changedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
        isRollback = false,
        rollbackFromVersion = null,
        status = status,
    )

    @Test
    fun `insert main round-trips with generated id`() {
        val saved =
            transaction {
                repository.insertMain(main("flow_repo_main"))
            }
        assertNotNull(saved.id)
        assertEquals("flow_repo_main", saved.flowKey)
        assertEquals(1, saved.version)
        assertEquals("ACTIVE", saved.status)
    }

    @Test
    fun `insert version round-trips with default draft status`() {
        val savedMain =
            transaction {
                repository.insertMain(main("flow_repo_ver"))
            }
        val saved =
            transaction {
                repository.insertVersion(version("flow_repo_ver", 1, flowId = savedMain.id!!))
            }
        assertNotNull(saved.id)
        assertEquals("DRAFT", saved.status)
        assertEquals(false, saved.isRollback)
    }

    @Test
    fun `find versions orders by version descending`() {
        val savedMain =
            transaction {
                repository.insertMain(main("flow_repo_order", version = 3))
            }
        transaction {
            repository.insertVersion(version("flow_repo_order", 1, flowId = savedMain.id!!))
            repository.insertVersion(version("flow_repo_order", 3, flowId = savedMain.id!!))
            repository.insertVersion(version("flow_repo_order", 2, flowId = savedMain.id!!))
        }

        val versions =
            transaction {
                repository.findVersionsByFlowKey("flow_repo_order")
            }
        assertEquals(listOf(3, 2, 1), versions.map { it.version })
    }

    @Test
    fun `update main pointer advances version and active pointer`() {
        val saved =
            transaction {
                repository.insertMain(main("flow_repo_ptr", version = 1, activeVersion = 1))
            }
        val updated =
            transaction {
                repository.updateMainPointer(
                    flowKey = "flow_repo_ptr",
                    version = 2,
                    activeVersion = 2,
                    flowGraph = """{"nodes":[{"id":"n2"}],"edges":[]}""",
                    updatedBy = "grayscale",
                )
            }
        assertEquals(1, updated)
        val reloaded =
            transaction {
                repository.findMain("flow_repo_ptr")
            }!!
        assertEquals(2, reloaded.version)
        assertEquals(2, reloaded.activeVersion)
        assertTrue(reloaded.flowGraph.contains("n2"))
        assertEquals(saved.createdAt, reloaded.createdAt)
    }

    @Test
    fun `update version status persists canary transition`() {
        val savedMain =
            transaction {
                repository.insertMain(main("flow_repo_status"))
            }
        transaction {
            repository.insertVersion(version("flow_repo_status", 2, flowId = savedMain.id!!))
        }

        val updated =
            transaction {
                repository.updateVersionStatus("flow_repo_status", 2, "CANARY")
            }
        assertEquals(1, updated)
        val reloaded =
            transaction {
                repository.findVersion("flow_repo_status", 2)
            }
        assertEquals("CANARY", reloaded?.status)
    }

    @Test
    fun `update main status and enabled persist`() {
        transaction {
            repository.insertMain(main("flow_repo_state"))
        }
        val statusUpdated =
            transaction {
                repository.updateMainStatus("flow_repo_state", "DELETED", "admin")
            }
        val enabledUpdated =
            transaction {
                repository.setMainEnabled("flow_repo_state", false, "admin")
            }
        assertEquals(1, statusUpdated)
        assertEquals(1, enabledUpdated)
        val reloaded =
            transaction {
                repository.findMain("flow_repo_state")
            }!!
        assertEquals("DELETED", reloaded.status)
        assertEquals(false, reloaded.enabled)
    }

    @Test
    fun `missing flow key returns null everywhere`() {
        val missing =
            transaction {
                repository.findMain("flow_repo_missing") to repository.findVersion("flow_repo_missing", 9)
            }
        assertNull(missing.first)
        assertNull(missing.second)
        val empty =
            transaction {
                repository.findVersionsByFlowKey("flow_repo_missing")
            }
        assertTrue(empty.isEmpty())
    }
}
