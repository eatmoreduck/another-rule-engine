package ruleenginespike

import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

/**
 * 阶段 2 前置探针：Exposed 1.5.0 × Spring Boot 4.1.1（Spring Framework 7）运行时兼容性验证。
 *
 * 背景：spring-transaction 1.5.0 声明依赖 Spring 6.2.19，Gradle 已仲裁到 Boot 4 的 Spring 7，
 * 本测试验证 SpringTransactionManager 在 Spring 7 字节码环境下真实可用。
 * 结论落地后此测试转为正式集成方案的回归用例。
 *
 * 说明：探针只关心事务桥接本身，故用纯 SpringExtension + @ContextConfiguration 装配
 * 自持的 H2 数据源与事务管理器，不启动 Boot 应用上下文（那需要真实 PostgreSQL；
 * 全链路集成由 AdminApiContractTest 承担）。
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [ExposedBoot4IntegrationTest.ExposedSpikeConfig::class])
class ExposedBoot4IntegrationTest {
    /**
     * @TestConfiguration（而非裸 @Configuration）：嵌套测试配置类带 @TestComponent 元注解，
     * 组件扫描会跳过——否则被全包扫描（com.example.ruleengine）的上下文拾取后，
     * spikeDataSource 与 storageDataSource 构成两个 DataSource 候选， spikeDatabase
     * 按类型注入即失败（NoUniqueBeanDefinitionException），拖垮全部 @SpringBootTest。
     */
    @TestConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    class ExposedSpikeConfig {
        @Bean
        fun spikeDataSource(): DataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:mem:spikedb;DB_CLOSE_DELAY=-1")
            }

        @Bean
        fun spikeDatabase(ds: DataSource): Database = Database.connect(ds)

        @Bean
        fun spikeTxManager(ds: DataSource): PlatformTransactionManager = SpringTransactionManager(ds, DatabaseConfig(), false)
    }

    @Test
    fun `standalone exposed transaction works on spring 7 classpath`() {
        // 纯 Exposed 事务路径：不经过 Spring 事务管理器
        transaction {
            SchemaUtils.create(SpikeTable)
        }
        transaction {
            SpikeTable.insert {
                it[name] = "standalone"
            }
            assertEquals(1L, SpikeTable.selectAll().count())
        }
    }

    @Test
    @Transactional
    fun `spring managed transaction interoperates with exposed`() {
        // Spring 事务路径：@Transactional 开启的事务上下文中嵌套执行 Exposed 操作，
        // 验证 SpringTransactionManager 在 Spring 7 上的 doBegin/doCommit 全链路
        transaction {
            SchemaUtils.create(SpikeTable)
            SpikeTable.insert {
                it[name] = "spring-tx"
            }
            assertEquals(1L, SpikeTable.selectAll().count())
        }
    }

    private object SpikeTable : Table("spike_t") {
        val name = varchar("name", 64)
    }
}
