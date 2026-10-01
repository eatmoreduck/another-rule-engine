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

    /** 获取连接超时（毫秒）；链路抖动场景下调小可快速失败、避免请求长时间挂起 */
    var connectionTimeoutMs: Long = 30_000

    /**
     * 空闲连接探活间隔（毫秒），映射 Hikari keepaliveTime，0 = 关闭。
     *
     * 跨网链路（如 ZeroTier 隧道）瞬断后池中会残留死连接，开启后由 Hikari
     * 周期性对空闲连接发探活查询并及时剔除，恢复后池子以秒级速度回满。
     * 默认 30s，须小于 Hikari 默认 maxLifetime（30min）。
     */
    var keepaliveTimeMs: Long = 30_000

    /** Flyway 迁移脚本位置（沿用旧后端迁移基线） */
    var flywayLocations: Array<String> = arrayOf("classpath:db/migration")
}
