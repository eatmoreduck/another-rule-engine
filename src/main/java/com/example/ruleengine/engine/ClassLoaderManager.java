package com.example.ruleengine.engine;

import groovy.lang.GroovyClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 类加载器管理器
 * Source: RESEARCH.md 陷阱1 + CONTEXT.md 决策 D-06
 *
 * 功能：
 * 1. 管理脚本 ClassLoader 生命周期
 * 2. 防止 Metaspace 内存泄漏
 * 3. 提供弱引用跟踪，支持 GC 回收
 */
@Component
public class ClassLoaderManager {

    private static final Logger logger = LoggerFactory.getLogger(ClassLoaderManager.class);

    // 使用弱引用跟踪 ClassLoader，允许 GC 回收
    private final ConcurrentHashMap<String, WeakReference<GroovyClassLoader>> loaders = new ConcurrentHashMap<>();

    /**
     * 获取或创建指定规则的 ClassLoader
     * D-06: 使用单例 GroovyClassLoader
     */
    public GroovyClassLoader getClassLoader(String ruleId) {
        WeakReference<GroovyClassLoader> ref = loaders.get(ruleId);

        if (ref != null && ref.get() != null) {
            return ref.get();
        }

        // 创建新的 ClassLoader
        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader());
        loaders.put(ruleId, new WeakReference<>(loader));

        logger.debug("Created new ClassLoader for rule: {}", ruleId);
        return loader;
    }

    /**
     * 清理指定规则的 ClassLoader
     */
    public void cleanUpClassLoader(String ruleId) {
        WeakReference<GroovyClassLoader> ref = loaders.remove(ruleId);

        if (ref != null) {
            GroovyClassLoader loader = ref.get();
            if (loader != null) {
                try {
                    loader.close();
                    logger.debug("Closed ClassLoader for rule: {}", ruleId);
                } catch (Exception e) {
                    logger.warn("Failed to close ClassLoader for rule: {}", ruleId, e);
                }
            }
        }
    }

    /**
     * 清理所有 ClassLoader
     */
    public void cleanUpAllLoaders() {
        loaders.forEach((ruleId, ref) -> {
            GroovyClassLoader loader = ref.get();
            if (loader != null) {
                try {
                    loader.close();
                } catch (Exception e) {
                    logger.warn("Failed to close ClassLoader for rule: {}", ruleId, e);
                }
            }
        });
        loaders.clear();
        logger.info("Closed all ClassLoaders");
    }

    /**
     * 获取活跃的 ClassLoader 数量
     */
    public int getActiveLoaderCount() {
        int count = 0;
        for (WeakReference<GroovyClassLoader> ref : loaders.values()) {
            if (ref.get() != null) {
                count++;
            }
        }
        return count;
    }
}
