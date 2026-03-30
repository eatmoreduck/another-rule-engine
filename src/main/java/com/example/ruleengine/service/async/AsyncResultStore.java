package com.example.ruleengine.service.async;

import com.example.ruleengine.model.DecisionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步执行结果存储
 * REXEC-02: 存储异步决策请求的执行结果
 *
 * 功能：
 * 1. 使用 ConcurrentHashMap 线程安全地存储异步执行结果
 * 2. 支持过期清理，防止内存泄漏
 * 3. 支持通过 requestId 查询结果
 */
@Component
public class AsyncResultStore {

    private static final Logger logger = LoggerFactory.getLogger(AsyncResultStore.class);

    /**
     * 结果条目，包含响应和创建时间
     */
    private static class ResultEntry {
        final DecisionResponse response;
        final long createdAt;

        ResultEntry(DecisionResponse response) {
            this.response = response;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired(long expireSeconds) {
            return (System.currentTimeMillis() - createdAt) > expireSeconds * 1000L;
        }
    }

    private final ConcurrentHashMap<String, ResultEntry> store = new ConcurrentHashMap<>();

    /** 结果过期时间（秒），默认 5 分钟 */
    private final long expireSeconds;

    public AsyncResultStore() {
        this.expireSeconds = 300;
    }

    /**
     * @param expireSeconds 结果过期时间（秒）
     */
    public AsyncResultStore(long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    /**
     * 存储异步执行结果
     *
     * @param requestId 请求ID
     * @param response  决策响应
     */
    public void storeResult(String requestId, DecisionResponse response) {
        if (requestId == null || requestId.isEmpty()) {
            logger.warn("requestId 为空，无法存储结果");
            return;
        }
        store.put(requestId, new ResultEntry(response));
        logger.debug("存储异步结果: requestId={}, decision={}", requestId, response.getDecision());
    }

    /**
     * 获取异步执行结果
     *
     * @param requestId 请求ID
     * @return 决策响应，如果不存在或已过期则返回 null
     */
    public DecisionResponse getResult(String requestId) {
        ResultEntry entry = store.get(requestId);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(expireSeconds)) {
            store.remove(requestId);
            logger.debug("异步结果已过期并清理: requestId={}", requestId);
            return null;
        }
        return entry.response;
    }

    /**
     * 检查结果是否存在
     *
     * @param requestId 请求ID
     * @return 是否存在
     */
    public boolean hasResult(String requestId) {
        ResultEntry entry = store.get(requestId);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired(expireSeconds)) {
            store.remove(requestId);
            return false;
        }
        return true;
    }

    /**
     * 移除指定结果
     *
     * @param requestId 请求ID
     */
    public void removeResult(String requestId) {
        store.remove(requestId);
    }

    /**
     * 定时清理过期结果
     * 每分钟执行一次
     */
    @Scheduled(fixedDelayString = "${rule-engine.async.cleanup-interval-seconds:60}000")
    public void cleanupExpiredResults() {
        int removedCount = 0;
        for (Map.Entry<String, ResultEntry> entry : store.entrySet()) {
            if (entry.getValue().isExpired(expireSeconds)) {
                store.remove(entry.getKey());
                removedCount++;
            }
        }
        if (removedCount > 0) {
            logger.info("清理过期异步结果: count={}", removedCount);
        }
    }

    /**
     * 获取当前存储的结果数量（用于监控）
     *
     * @return 结果数量
     */
    public int size() {
        return store.size();
    }
}
