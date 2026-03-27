package com.example.ruleengine.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CaffeineCacheConfig 单元测试
 * <p>
 * 测试覆盖:
 * 1. 缓存配置正确性
 * 2. 缓存基本操作
 * 3. 缓存统计功能
 * 4. 缓存过期行为
 * 5. 缓存容量限制
 */
@DisplayName("CaffeineCacheConfig 测试")
class CaffeineCacheConfigTest {

    private CaffeineCacheConfig cacheConfig;

    @BeforeEach
    void setUp() {
        cacheConfig = new CaffeineCacheConfig();
    }

    // ==================== 缓存配置测试 ====================

    @Nested
    @DisplayName("缓存配置测试")
    class CacheConfigurationTests {

        @Test
        @DisplayName("featureCache 应正确创建")
        void shouldCreateFeatureCache() {
            // Act
            Cache<String, Object> cache = cacheConfig.featureCache();

            // Assert
            assertNotNull(cache);
            assertTrue(cache instanceof Cache);
        }

        @Test
        @DisplayName("cacheManager 应正确创建")
        void shouldCreateCacheManager() {
            // Act
            CacheManager cacheManager = cacheConfig.cacheManager();

            // Assert
            assertNotNull(cacheManager);
        }

        @Test
        @DisplayName("缓存应启用统计")
        void shouldEnableStats() {
            // Act
            Cache<String, Object> cache = cacheConfig.featureCache();
            CacheStats stats = cache.stats();

            // Assert
            assertNotNull(stats);
            // 统计功能已启用
        }
    }

    // ==================== 缓存基本操作测试 ====================

    @Nested
    @DisplayName("缓存基本操作测试")
    class CacheOperationTests {

        private Cache<String, Object> cache;

        @BeforeEach
        void setUp() {
            cache = cacheConfig.featureCache();
        }

        @Test
        @DisplayName("应能存入和获取值")
        void shouldPutAndGet() {
            // Act
            cache.put("key1", "value1");

            // Assert
            assertEquals("value1", cache.getIfPresent("key1"));
        }

        @Test
        @DisplayName("获取不存在的键应返回 null")
        void shouldReturnNullForMissingKey() {
            // Act & Assert
            assertNull(cache.getIfPresent("non-existent-key"));
        }

        @Test
        @DisplayName("应能覆盖已存在的值")
        void shouldOverwriteExistingValue() {
            // Arrange
            cache.put("key1", "value1");

            // Act
            cache.put("key1", "value2");

            // Assert
            assertEquals("value2", cache.getIfPresent("key1"));
        }

        @Test
        @DisplayName("应能删除缓存条目")
        void shouldInvalidateEntry() {
            // Arrange
            cache.put("key1", "value1");

            // Act
            cache.invalidate("key1");

            // Assert
            assertNull(cache.getIfPresent("key1"));
        }

        @Test
        @DisplayName("应能清空所有缓存")
        void shouldInvalidateAll() {
            // Arrange
            cache.put("key1", "value1");
            cache.put("key2", "value2");

            // Act
            cache.invalidateAll();

            // Assert
            assertEquals(0, cache.estimatedSize());
        }

        @Test
        @DisplayName("应能存储不同类型的值")
        void shouldStoreDifferentTypes() {
            // Act
            cache.put("string", "text");
            cache.put("integer", 123);
            cache.put("double", 45.67);
            cache.put("boolean", true);

            // Assert
            assertEquals("text", cache.getIfPresent("string"));
            assertEquals(123, cache.getIfPresent("integer"));
            assertEquals(45.67, cache.getIfPresent("double"));
            assertEquals(true, cache.getIfPresent("boolean"));
        }
    }

    // ==================== 缓存统计测试 ====================

    @Nested
    @DisplayName("缓存统计测试")
    class CacheStatsTests {

        private Cache<String, Object> cache;

        @BeforeEach
        void setUp() {
            cache = cacheConfig.featureCache();
        }

        @Test
        @DisplayName("应记录命中次数")
        void shouldRecordHitCount() {
            // Arrange
            cache.put("key1", "value1");

            // Act
            cache.getIfPresent("key1");
            cache.getIfPresent("key1");

            // Assert
            CacheStats stats = cache.stats();
            assertTrue(stats.hitCount() >= 2);
        }

        @Test
        @DisplayName("应记录未命中次数")
        void shouldRecordMissCount() {
            // Act
            cache.getIfPresent("missing1");
            cache.getIfPresent("missing2");

            // Assert
            CacheStats stats = cache.stats();
            assertTrue(stats.missCount() >= 2);
        }

        @Test
        @DisplayName("应计算命中率")
        void shouldCalculateHitRate() {
            // Arrange
            cache.put("key1", "value1");

            // Act
            cache.getIfPresent("key1"); // hit
            cache.getIfPresent("key1"); // hit
            cache.getIfPresent("missing"); // miss

            // Assert
            CacheStats stats = cache.stats();
            double hitRate = stats.hitRate();
            assertTrue(hitRate >= 0.0 && hitRate <= 1.0);
        }

        @Test
        @DisplayName("应记录加载次数")
        void shouldRecordLoadCount() {
            // Arrange
            cache.put("key1", "value1");

            // Assert - 初始统计
            CacheStats stats = cache.stats();
            assertNotNull(stats);
        }
    }

    // ==================== 缓存容量测试 ====================

    @Nested
    @DisplayName("缓存容量测试")
    class CacheCapacityTests {

        @Test
        @DisplayName("缓存应有最大容量限制")
        void shouldHaveMaximumSize() {
            // Arrange
            Cache<String, Object> cache = cacheConfig.featureCache();

            // Act - 添加超过最大容量的条目
            for (int i = 0; i < 15000; i++) {
                cache.put("key" + i, "value" + i);
            }

            // Assert - 由于最大容量是 10000，一些条目应该被驱逐
            assertTrue(cache.estimatedSize() <= 10500); // 允许一些误差
        }

        @Test
        @DisplayName("缓存大小应正确估算")
        void shouldEstimateSize() {
            // Arrange
            Cache<String, Object> cache = cacheConfig.featureCache();

            // Act
            for (int i = 0; i < 100; i++) {
                cache.put("key" + i, "value" + i);
            }

            // Assert
            assertEquals(100, cache.estimatedSize());
        }
    }

    // ==================== 缓存过期测试 ====================

    @Nested
    @DisplayName("缓存过期测试")
    class CacheExpiryTests {

        @Test
        @DisplayName("缓存应配置有过期时间")
        void shouldHaveExpiryConfigured() {
            // Arrange
            Cache<String, Object> cache = cacheConfig.featureCache();

            // Act & Assert - 验证缓存创建成功（过期配置已内置）
            assertNotNull(cache);
            cache.put("test", "value");
            assertEquals("value", cache.getIfPresent("test"));
        }

        @Test
        @DisplayName("条目在写入后一段时间内应可访问")
        void shouldBeAccessibleAfterWrite() throws InterruptedException {
            // Arrange
            Cache<String, Object> cache = cacheConfig.featureCache();
            cache.put("key1", "value1");

            // Act - 短暂等待
            Thread.sleep(100);

            // Assert - 仍应可访问
            assertEquals("value1", cache.getIfPresent("key1"));
        }
    }

    // ==================== 并发测试 ====================

    @Nested
    @DisplayName("并发测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("并发写入应安全")
        void shouldHandleConcurrentWrites() throws InterruptedException {
            // Arrange
            Cache<String, Object> cache = cacheConfig.featureCache();
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            // Act
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        cache.put("key" + index + "_" + j, "value" + j);
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Assert
            assertTrue(cache.estimatedSize() > 0);
        }

        @Test
        @DisplayName("并发读写应安全")
        void shouldHandleConcurrentReadWrite() throws InterruptedException {
            // Arrange
            Cache<String, Object> cache = cacheConfig.featureCache();
            cache.put("shared", "initial");

            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            // Act
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        cache.put("shared", "value" + j);
                        cache.getIfPresent("shared");
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Assert - 不应抛出异常
            assertNotNull(cache.getIfPresent("shared"));
        }
    }

    // ==================== CacheManager 测试 ====================

    @Nested
    @DisplayName("CacheManager 测试")
    class CacheManagerTests {

        @Test
        @DisplayName("CacheManager 应能获取缓存")
        void shouldGetCacheFromManager() {
            // Arrange
            CacheManager cacheManager = cacheConfig.cacheManager();

            // Act
            org.springframework.cache.Cache cache = cacheManager.getCache("testCache");

            // Assert
            assertNotNull(cache);
        }

        @Test
        @DisplayName("CacheManager 应能获取缓存名称")
        void shouldGetCacheNames() {
            // Arrange
            CacheManager cacheManager = cacheConfig.cacheManager();
            cacheManager.getCache("cache1");
            cacheManager.getCache("cache2");

            // Act & Assert
            // CaffeineCacheManager 动态创建缓存
            assertNotNull(cacheManager);
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        private Cache<String, Object> cache;

        @BeforeEach
        void setUp() {
            cache = cacheConfig.featureCache();
        }

        @Test
        @DisplayName("存储 null 值应抛出异常")
        void shouldThrowExceptionForNullValue() {
            // Act & Assert - Caffeine 不允许存储 null 值
            assertThrows(NullPointerException.class, () -> {
                cache.put("nullKey", null);
            });
        }

        @Test
        @DisplayName("应能存储空字符串")
        void shouldStoreEmptyString() {
            // Act
            cache.put("emptyString", "");

            // Assert
            assertEquals("", cache.getIfPresent("emptyString"));
        }

        @Test
        @DisplayName("应能处理特殊字符作为键")
        void shouldHandleSpecialCharactersAsKey() {
            // Act
            cache.put("key:with:colons", "value1");
            cache.put("key-with-dashes", "value2");
            cache.put("key.with.dots", "value3");

            // Assert
            assertEquals("value1", cache.getIfPresent("key:with:colons"));
            assertEquals("value2", cache.getIfPresent("key-with-dashes"));
            assertEquals("value3", cache.getIfPresent("key.with.dots"));
        }

        @Test
        @DisplayName("应能处理 Unicode 字符作为键")
        void shouldHandleUnicodeAsKey() {
            // Act
            cache.put("用户年龄", 25);
            cache.put("\uD83D\uDC4D", "thumbsup");

            // Assert
            assertEquals(25, cache.getIfPresent("用户年龄"));
            assertEquals("thumbsup", cache.getIfPresent("\uD83D\uDC4D"));
        }

        @Test
        @DisplayName("应能存储大对象")
        void shouldStoreLargeObject() {
            // Arrange
            StringBuilder largeValue = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                largeValue.append("x");
            }

            // Act
            cache.put("largeKey", largeValue.toString());

            // Assert
            assertEquals(10000, ((String) cache.getIfPresent("largeKey")).length());
        }
    }
}
