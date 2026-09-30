package com.example.ruleengine.decision.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 决策链路运行时配置（前缀 ruleengine.decision）。
 *
 * 采用 JavaBean setter 绑定（可变属性），理由同 storage 的 [StorageProperties]：
 * 稳定兼容、不依赖参数名保留。
 *
 * 性能口径：产品核心约束"单次决策 < 50ms"，所有超时与缓存参数都必须显式配置化，
 * 默认值面向本地开发（外部特征源默认关闭）。
 */
@ConfigurationProperties(prefix = "ruleengine.decision")
class DecisionProperties {
    /**
     * 规则脚本执行超时（engine 沙箱的中断上限）。
     * 请求可携带更小的 timeoutMs（契约默认 50ms），实际生效值为两者取小；
     * 本值为兜底上限，防止恶意请求携带超大超时拖垮决策节点。
     */
    var executionTimeoutMs: Long = 200

    /** 特征获取超时（对应旧 FeatureRequest 默认 20ms） */
    var featureTimeoutMs: Long = 20

    /**
     * 外部特征平台地址（POST，请求体为缺失特征编码列表，响应为键值 Map）。
     * NULL/空白表示禁用外部获取，缺失特征直接走本地缓存与默认值降级。
     * 默认禁用：旧实现的外部平台地址在容器外不可达（异常即空 Map），常态等价。
     */
    var featureExternalUrl: String? = null

    /** 特征值缓存容量上限（外部获取结果回填；键为规范特征编码） */
    var featureCacheMaximumSize: Long = 5_000

    /** 特征值缓存写入后过期时间（秒）：特征值新鲜度与外部源打点频率的折中 */
    var featureCacheExpireAfterWriteSeconds: Long = 10

    /** 特征编码别名解析缓存容量上限（别名/规范编码映射，变更频率低） */
    var featureCodeCacheMaximumSize: Long = 2_000

    /** 特征编码别名解析缓存过期时间（秒） */
    var featureCodeCacheExpireAfterWriteSeconds: Long = 300

    /** 规则/流图快照缓存容量上限（键为 (key, version)） */
    var snapshotCacheMaximumSize: Long = 1_000

    /** 规则主行快照缓存容量上限（键为 ruleKey / flowKey） */
    var mainCacheMaximumSize: Long = 500

    /** 快照缓存写入后过期时间（秒）：短 TTL 兜底规则发布/灰度切换的时效性（阶段 5 引入 Redis pub/sub 即时失效） */
    var snapshotExpireAfterWriteSeconds: Long = 30

    /** 灰度配置缓存过期时间（秒）：分流策略变更需要最快生效，短于规则快照（旧实现为 10s） */
    var grayscaleExpireAfterWriteSeconds: Long = 10

    /** 黑白名单查询缓存容量上限 */
    var nameListCacheMaximumSize: Long = 10_000

    /** 黑白名单查询缓存过期时间（秒）：名单新增/删除的生效延迟上限 */
    var nameListExpireAfterWriteSeconds: Long = 5

    /** 决策流最大遍历步数：防止流图成环导致无限遍历（旧递归实现无保护） */
    var flowMaxSteps: Int = 1000

    /** 执行日志缓冲队列容量（满后丢弃最旧日志并计数，绝不阻塞决策链路） */
    var logBufferCapacity: Int = 10_000

    /** 执行日志单批刷盘行数 */
    var logBatchSize: Int = 100

    /** 执行日志刷盘间隔（毫秒）：攒批等待窗口，兼顾落库吞吐与查询延迟 */
    var logFlushIntervalMs: Long = 500

    /** 异步决策结果过期时间（秒），对应旧 AsyncResultStore 的 5 分钟 */
    var asyncResultExpireSeconds: Long = 300

    /** 异步决策结果过期清理间隔（秒） */
    var asyncCleanupIntervalSeconds: Long = 60
}
