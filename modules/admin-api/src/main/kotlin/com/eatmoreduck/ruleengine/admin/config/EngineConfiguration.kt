package com.eatmoreduck.ruleengine.admin.config

import com.eatmoreduck.ruleengine.engine.GroovyScriptEngine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 沙箱脚本引擎装配：校验链使用（静态审计 + 沙箱编译，不执行脚本）。
 * 引擎持有编译缓存与隔离类加载器，容器关闭时整体释放（destroyMethod="close"）。
 */
@Configuration(proxyBeanMethods = false)
class EngineConfiguration {
    @Bean(destroyMethod = "close")
    fun groovyScriptEngine(): GroovyScriptEngine = GroovyScriptEngine()
}
