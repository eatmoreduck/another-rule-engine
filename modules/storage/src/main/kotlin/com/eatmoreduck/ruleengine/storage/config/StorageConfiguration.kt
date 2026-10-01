package com.eatmoreduck.ruleengine.storage.config

import com.eatmoreduck.ruleengine.storage.repository.ExposedFeatureCatalogRepository
import com.eatmoreduck.ruleengine.storage.repository.ExposedGrayscaleReleaseRepository
import com.eatmoreduck.ruleengine.storage.repository.ExposedRuleRepository
import com.eatmoreduck.ruleengine.storage.repository.ExposedRuleVersionRepository
import com.eatmoreduck.ruleengine.storage.repository.FeatureCatalogRepository
import com.eatmoreduck.ruleengine.storage.repository.GrayscaleReleaseRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleVersionRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * 持久化层装配：DataSource → Exposed Database → SpringTransactionManager + Flyway 基线。
 *
 * 形态来源：admin-api 探针（ExposedBoot4IntegrationTest）在 Boot 4.1.1 / Spring 7 上
 * 验证通过后转正。要点：
 * - [Database.connect] 建立全局默认连接对象，Spring 事务之外的手写 `transaction { }`（测试、脚本）
 *   也走它；
 * - [SpringTransactionManager] 把 Spring `@Transactional` 事务桥接为 Exposed 事务，
 *   仓储实现不自行开启事务，事务边界由服务层声明；
 * - useSavepoints 显式置 false（与探针一致）：Exposed 的嵌套事务经 Spring 保存点模拟，
 *   PostgreSQL 下关闭以降低开销；
 * - Flyway 在容器启动期执行迁移（迁移基线 = 旧后端 V1..V25，随本模块打包在 classpath:db/migration）；
 * - proxyBeanMethods=false 轻量模式：bean 之间零互调（依赖全部经方法参数注入），
 *   Kotlin 类无需 open。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties::class)
class StorageConfiguration {
    @Bean(destroyMethod = "close")
    fun storageDataSource(properties: StorageProperties): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = properties.url
                username = properties.username
                password = properties.password
                maximumPoolSize = properties.maximumPoolSize
                minimumIdle = properties.minimumIdle
                connectionTimeout = properties.connectionTimeoutMs
                if (properties.keepaliveTimeMs > 0) keepaliveTime = properties.keepaliveTimeMs
                // PostgreSQL 驱动经 URL 自动识别，无需显式 driverClassName
                poolName = "ruleengine-storage"
            },
        )

    /** Exposed 全局默认数据库（非 Spring 事务路径使用） */
    @Bean
    fun exposedDatabase(dataSource: DataSource): Database = Database.connect(dataSource)

    /** 启动期执行 Flyway 迁移（旧后端 V1..V25 基线） */
    @Bean
    fun storageFlyway(
        dataSource: DataSource,
        properties: StorageProperties,
    ): Flyway =
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations(*properties.flywayLocations)
            .load()
            .apply { migrate() }

    /** Spring 事务管理器：@Transactional 上下文与 Exposed DSL 的桥接 */
    @Bean
    fun exposedTransactionManager(dataSource: DataSource): PlatformTransactionManager =
        SpringTransactionManager(dataSource, DatabaseConfig(), false)

    @Bean
    fun ruleRepository(): RuleRepository = ExposedRuleRepository()

    @Bean
    fun ruleVersionRepository(): RuleVersionRepository = ExposedRuleVersionRepository()

    @Bean
    fun grayscaleReleaseRepository(): GrayscaleReleaseRepository = ExposedGrayscaleReleaseRepository()

    @Bean
    fun featureCatalogRepository(): FeatureCatalogRepository = ExposedFeatureCatalogRepository()
}
