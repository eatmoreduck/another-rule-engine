package com.eatmoreduck.ruleengine.storage.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 持久化层连接配置（前缀 ruleengine.storage）。
 *
 * 采用 JavaBean setter 绑定（可变属性 + 类体字段）而非构造器绑定：
 * storage 模块为纯 Kotlin 库，编译不经 Boot 插件注入 -parameters，
 * JavaBean 绑定不依赖参数名保留，兼容性最稳。
 *
 * 默认值仅为本地开发占位，部署模块经 application.yml 覆盖：
 * ```yaml
 * ruleengine:
 *   storage:
 *     url: jdbc:postgresql://192.168.5.202:5432/yare_engine
 *     username: leon
 *     password: ${DB_PASSWORD}
 * ```
 */
@ConfigurationProperties(prefix = "ruleengine.storage")
class StorageProperties {
    /** JDBC 连接串 */
    var url: String = "jdbc:postgresql://localhost:5432/ruleengine"

    /** 数据库用户名 */
    var username: String = "postgres"

    /** 数据库密码 */
    var password: String = ""

    /** Hikari 连接池最大连接数 */
    var maximumPoolSize: Int = 10

    /** Hikari 连接池最小空闲连接数 */
    var minimumIdle: Int = 2

    /** 获取连接超时（毫秒） */
    var connectionTimeoutMs: Long = 30_000

    /** Flyway 迁移脚本位置（沿用旧后端迁移基线） */
    var flywayLocations: Array<String> = arrayOf("classpath:db/migration")
}
