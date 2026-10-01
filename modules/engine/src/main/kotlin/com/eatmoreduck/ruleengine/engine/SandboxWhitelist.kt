package com.eatmoreduck.ruleengine.engine

import java.util.regex.Pattern

/**
 * 沙箱安全常量集中管理：白名单、接收者类黑名单、审计危险模式、各类阈值。
 *
 * 内容自旧 Java 实现（SecurityConfiguration / SecurityAuditService / ScriptCacheManager）
 * 完整移植并整理，是沙箱安全语义的唯一事实源：
 *
 * 第一层（编译期，见 [SandboxConfiguration]）：
 * 1. [ALLOWED_IMPORTS] 导入白名单——只允许安全类被显式导入
 * 2. [ALLOWED_STAR_IMPORTS] 星号导入白名单
 * 3. 静态导入 / 静态星号导入一律禁止（空白名单）
 * 4. [BLACKLISTED_RECEIVER_CLASSES] 接收者类黑名单——禁止对危险类实例调用任何方法
 *
 * 第二层（编译前文本预检，见 [ScriptAuditor]）：
 * 5. [DANGEROUS_PATTERNS] 危险 API 正则清单（含 Unicode 转义、注释隐藏、字符串拆分等绕过防御）
 * 6. [INFINITE_LOOP_PATTERN] 无限循环模式（警告级别）
 *
 * 修改本文件中任何清单后必须递增 [SANDBOX_CONFIG_VERSION]，
 * 使编译缓存（key = SHA-256(配置版本 + 脚本文本)）自动失效并重新编译。
 */
object SandboxWhitelist {
    /** 沙箱配置版本号：参与缓存 key 计算，清单变更时必须递增 */
    const val SANDBOX_CONFIG_VERSION: String = "1"

    // ---------------- 导入白名单（SecureASTCustomizer.importsWhitelist） ----------------

    /**
     * 允许脚本显式 import 的类与包。
     * 只收录与规则计算相关的纯数据/工具类；java.io / java.net / java.lang.reflect 等一律不放行。
     * 注意：SecureASTCustomizer 不允许同时设置导入白名单和黑名单，故这里只设白名单。
     */
    val ALLOWED_IMPORTS: List<String> =
        listOf(
            "java.util.*",
            "java.lang.Math",
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Boolean",
            "java.lang.Object",
            "java.lang.Character",
            "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Number",
            "java.lang.CharSequence",
            "java.lang.Comparable",
            "java.lang.Iterable",
            // java.time 包
            "java.time.*",
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.LocalTime",
            "java.time.ZoneId",
            "java.time.ZonedDateTime",
            "java.time.format.DateTimeFormatter",
            "java.time.Duration",
            "java.time.Period",
            "java.time.Instant",
            // java.math 包
            "java.math.BigDecimal",
            "java.math.BigInteger",
            "java.math.RoundingMode",
        )

    /** 允许星号导入（import xxx.*）的包 */
    val ALLOWED_STAR_IMPORTS: List<String> =
        listOf(
            "java.util",
            "java.time",
            "java.lang",
            "java.math",
        )

    /**
     * 接收者类黑名单：禁止脚本对这些类的实例调用任何方法/属性。
     * 基于 Class 对象匹配（receiversClassesBlackList），比字符串匹配更可靠。
     * 覆盖：System.exit / Runtime / ProcessBuilder / 反射（Class）/ 线程 / 类加载器。
     */
    val BLACKLISTED_RECEIVER_CLASSES: List<Class<*>> =
        listOf(
            // 系统操作
            java.lang.System::class.java,
            Runtime::class.java,
            ProcessBuilder::class.java,
            Process::class.java,
            // 反射操作
            Class::class.java,
            // 线程操作
            Thread::class.java,
            ThreadGroup::class.java,
            // 类加载器操作
            ClassLoader::class.java,
            java.net.URLClassLoader::class.java,
        )

    // ---------------- 静态审计危险模式（正则级文本预检） ----------------

    /**
     * 单条静态审计规则。
     *
     * @property name 危险类别名（出现在错误信息中）
     * @property pattern 匹配危险 API 的正则
     * @property reason 为什么禁止（出现在错误信息中，帮助规则作者理解并修正）
     */
    data class DangerousPattern(
        val name: String,
        val pattern: Pattern,
        val reason: String,
    )

    /**
     * 危险 API 正则清单（完整移植旧 SecurityAuditService 的全部 14 组模式，一条不少）。
     * 匹配对象是经 [ScriptAuditor.normalize] 归一化后的脚本文本。
     */
    val DANGEROUS_PATTERNS: List<DangerousPattern> =
        listOf(
            DangerousPattern(
                name = "System 调用",
                pattern = Pattern.compile("\\bSystem\\s*\\."),
                reason = "禁止访问 java.lang.System（防止退出 JVM、读取环境变量或系统属性）",
            ),
            DangerousPattern(
                name = "Runtime 调用",
                pattern = Pattern.compile("\\bRuntime\\s*\\.|getRuntime\\s*\\(\\s*\\)"),
                reason = "禁止访问 java.lang.Runtime（防止执行外部命令或控制 JVM 生命周期）",
            ),
            DangerousPattern(
                name = "进程创建",
                pattern = Pattern.compile("\\bProcessBuilder\\b|\\.exec\\s*\\("),
                reason = "禁止创建或执行操作系统进程",
            ),
            DangerousPattern(
                name = "文件操作",
                pattern =
                    Pattern.compile(
                        "\\bnew\\s+File\\s*\\(|\\bnew\\s+FileInputStream\\s*\\(|" +
                            "\\bnew\\s+FileOutputStream\\s*\\(|\\bnew\\s+FileWriter\\s*\\(|" +
                            "\\bnew\\s+FileReader\\s*\\(|\\bnew\\s+RandomAccessFile\\s*\\(|" +
                            "\\.delete\\s*\\(\\s*\\)",
                    ),
                reason = "禁止文件读取、写入与删除操作",
            ),
            DangerousPattern(
                name = "网络操作",
                pattern =
                    Pattern.compile(
                        "\\bnew\\s+URL\\s*\\(|\\bnew\\s+Socket\\s*\\(|" +
                            "\\bnew\\s+ServerSocket\\s*\\(|\\bHttpURLConnection\\b|" +
                            "\\bnew\\s+InetAddress\\s*\\(",
                    ),
                reason = "禁止网络连接与远程访问",
            ),
            DangerousPattern(
                name = "反射操作",
                pattern =
                    Pattern.compile(
                        "Class\\.forName\\s*\\(|\\.getClass\\s*\\(\\s*\\)|" +
                            "\\.getDeclaredMethod\\s*\\(|\\.getDeclaredField\\s*\\(|" +
                            "\\.getDeclaredConstructor\\s*\\(|\\.setAccessible\\s*\\(|" +
                            "\\.invoke\\s*\\(|java\\.lang\\.reflect\\.",
                    ),
                reason = "禁止反射（可绕过编译期白名单访问任意 API）",
            ),
            DangerousPattern(
                name = "线程操作",
                pattern =
                    Pattern.compile(
                        "\\bnew\\s+Thread\\s*\\(|\\.start\\s*\\(\\s*\\)|" +
                            "\\bThread\\.sleep\\s*\\(|\\bThread\\.currentThread\\s*\\(",
                    ),
                reason = "禁止创建或操控线程",
            ),
            DangerousPattern(
                name = "ClassLoader 操作",
                pattern = Pattern.compile("\\bClassLoader\\b|\\.loadClass\\s*\\(|\\.defineClass\\s*\\("),
                reason = "禁止动态加载类",
            ),
            DangerousPattern(
                name = "Groovy 内部调用",
                pattern =
                    Pattern.compile(
                        "\\bGroovyShell\\b|\\bGroovyClassLoader\\b|\\bCompilerConfiguration\\b|" +
                            "\\bEvaluate\\s*\\(",
                    ),
                reason = "禁止在脚本内再嵌套执行 Groovy 引擎",
            ),
            DangerousPattern(
                name = "eval 动态代码评估",
                pattern = Pattern.compile("\\beval\\s*\\("),
                reason = "禁止 eval 动态代码评估",
            ),
            DangerousPattern(
                name = "ScriptEngine 动态脚本执行",
                pattern = Pattern.compile("\\bScriptEngine\\b|\\bScriptEngineManager\\b"),
                reason = "禁止通过 ScriptEngine 再嵌套执行动态脚本",
            ),
            DangerousPattern(
                name = "ProcessBuilder 进程执行",
                pattern = Pattern.compile("\\bProcessBuilder\\b"),
                reason = "禁止通过 ProcessBuilder 执行系统命令",
            ),
            DangerousPattern(
                name = "Thread 线程操控",
                pattern =
                    Pattern.compile(
                        "\\bThread\\.currentThread\\s*\\(\\s*\\)|\\.interrupt\\s*\\(\\s*\\)|" +
                            "\\.setDaemon\\s*\\(\\s*\\)|\\.setPriority\\s*\\(\\s*\\)",
                    ),
                reason = "禁止操控线程状态（中断、守护、优先级）",
            ),
            DangerousPattern(
                name = "Class.forName 反射加载类",
                pattern = Pattern.compile("\\bClass\\.forName\\s*\\("),
                reason = "禁止通过 Class.forName 反射加载任意类",
            ),
        )

    /** 无限循环模式（警告级别：不拒绝脚本，由执行期超时中断兜底） */
    val INFINITE_LOOP_PATTERN: Pattern =
        Pattern.compile(
            "\\bwhile\\s*\\(\\s*true\\s*\\)|\\bfor\\s*\\(\\s*;\\s*;\\s*\\)|" +
                "\\bwhile\\s*\\(\\s*1\\s*\\)",
        )

    // ---------------- 各类阈值 ----------------

    /** 脚本最大长度限制（防止超大脚本消耗资源） */
    const val MAX_SCRIPT_LENGTH: Int = 65536

    /** 最大嵌套深度（防止深度递归/嵌套导致栈溢出，超出仅告警） */
    const val MAX_NESTING_DEPTH: Int = 10

    /** 编译缓存最大条目数（同旧 ScriptCacheManager.MAX_CACHE_SIZE） */
    const val MAX_CACHE_ENTRIES: Long = 1000

    /** 编译缓存条目空闲过期时间（同旧实现 24 小时） */
    val CACHE_EXPIRE_AFTER_ACCESS: java.time.Duration = java.time.Duration.ofHours(24)

    /** 默认脚本执行超时（超时后中断脚本线程并抛出 ScriptTimeoutException） */
    val DEFAULT_EXECUTION_TIMEOUT: java.time.Duration = java.time.Duration.ofSeconds(5)
}
