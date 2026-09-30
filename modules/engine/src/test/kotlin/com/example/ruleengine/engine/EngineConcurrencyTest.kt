package com.example.ruleengine.engine

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 并发测试：多线程并发执行相同 / 不同脚本，以及并发编译的防重复（防缓存击穿）能力。
 */
class EngineConcurrencyTest {
    @Test
    fun `concurrent executions of the same script produce correct results`() {
        // 多线程并发执行同一脚本（不同变量）应全部得到正确结果
        GroovyScriptEngine().use { engine ->
            val script = "return amount > threshold ? 'REJECT' : 'PASS'"
            val threadCount = 8
            val iterationsPerThread = 25
            val pool = Executors.newFixedThreadPool(threadCount)
            val futures = mutableListOf<Future<Pair<Long, Any?>>>()
            for (threadIndex in 0 until threadCount) {
                for (iteration in 0 until iterationsPerThread) {
                    val amount = threadIndex * 1000L + iteration
                    futures.add(
                        pool.submit(
                            Callable {
                                val result = engine.execute(script, mapOf("amount" to amount, "threshold" to 1500L))
                                amount to result
                            },
                        ),
                    )
                }
            }
            futures.forEach { future ->
                val (amount, result) = future.get(60, TimeUnit.SECONDS)
                val expected = if (amount > 1500) "REJECT" else "PASS"
                assertEquals(expected, result, "amount=$amount")
            }
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `concurrent executions of different scripts produce correct results`() {
        // 多线程并发编译并执行不同脚本应互不干扰
        GroovyScriptEngine().use { engine ->
            val threadCount = 8
            val pool = Executors.newFixedThreadPool(threadCount)
            val futures: List<Future<Pair<Int, Any?>>> =
                (0 until threadCount).map { threadIndex ->
                    val script = "return x * $threadIndex + 1"
                    pool.submit(
                        Callable {
                            val result = engine.execute(script, mapOf("x" to 10))
                            threadIndex to result
                        },
                    )
                }
            futures.forEach { future ->
                val (threadIndex, result) = future.get(60, TimeUnit.SECONDS)
                assertEquals(10 * threadIndex + 1, result, "threadIndex=$threadIndex")
            }
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `concurrent compiles of the same script compile only once`() {
        // 并发编译同一脚本应只编译一次（Caffeine 按 key 原子加载，防缓存击穿）
        GroovyScriptEngine().use { engine ->
            val script = "return 'cached-' + n"
            val threadCount = 8
            val barrier = CyclicBarrier(threadCount)
            val pool = Executors.newFixedThreadPool(threadCount)
            val futures: List<Future<CompiledScript>> =
                (0 until threadCount).map {
                    pool.submit(
                        Callable {
                            barrier.await(10, TimeUnit.SECONDS)
                            engine.compile(script)
                        },
                    )
                }
            val results = futures.map { it.get(60, TimeUnit.SECONDS) }
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
            results.forEach { compiled -> assertSame(results[0], compiled, "并发编译应返回同一实例") }
            assertEquals(1, engine.compileCount, "并发编译同一脚本只应编译一次")
        }
    }

    @Test
    fun `concurrent executions sharing one compiled script stay isolated`() {
        // 同一编译产物被多线程共享执行时，变量绑定互不串扰
        GroovyScriptEngine().use { engine ->
            val script = "return name + '-' + value"
            val threadCount = 8
            val iterations = 20
            val pool = Executors.newFixedThreadPool(threadCount)
            val futures = mutableListOf<Future<Pair<String, Any?>>>()
            for (threadIndex in 0 until threadCount) {
                for (iteration in 0 until iterations) {
                    val name = "user-$threadIndex"
                    val value = iteration
                    futures.add(
                        pool.submit(
                            Callable {
                                val result = engine.execute(script, mapOf("name" to name, "value" to value))
                                "$name-$value" to result
                            },
                        ),
                    )
                }
            }
            futures.forEach { future ->
                val (expected, result) = future.get(60, TimeUnit.SECONDS)
                assertEquals(expected, result)
            }
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
            assertEquals(1, engine.compileCount, "共享脚本只应编译一次")
        }
    }
}
