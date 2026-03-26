---
phase: 1
slug: core-engine
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2025-03-26
verified: 2025-03-26
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | src/test/resources/application-test.yml |
| **Quick run command** | `./gradlew test --tests "*RuleEngineTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*{TestClass}"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | REXEC-01 | integration | `./gradlew test --tests "*DecisionApiTest"` | ❌ W0 | ⬜ pending |
| 01-01-02 | 01 | 1 | REXEC-03 | unit | `./gradlew test --tests "*GroovyExecutorTest"` | ❌ W0 | ⬜ pending |
| 01-01-03 | 01 | 1 | REXEC-04 | unit | `./gradlew test --tests "*FeatureProviderTest"` | ❌ W0 | ⬜ pending |
| 01-02-01 | 02 | 1 | PERF-01 | unit | `./gradlew test --tests "*ScriptCacheTest"` | ❌ W0 | ⬜ pending |
| 01-02-02 | 02 | 1 | PERF-02 | unit | `./gradlew test --tests "*FeatureCacheTest"` | ❌ W0 | ⬜ pending |
| 01-03-01 | 03 | 1 | SEC-02 | unit | `./gradlew test --tests "*SandboxTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/engine/DecisionApiTest.java` — 同步 API 决策接口测试 (REXEC-01)
- [ ] `src/test/java/com/engine/GroovyExecutorTest.java` — Groovy 脚本执行测试 (REXEC-03)
- [ ] `src/test/java/com/engine/FeatureProviderTest.java` — 特征获取策略测试 (REXEC-04)
- [ ] `src/test/java/com/performance/ScriptCacheTest.java` — 脚本缓存测试 (PERF-01)
- [ ] `src/test/java/com/performance/FeatureCacheTest.java` — 特征缓存测试 (PERF-02)
- [ ] `src/test/java/com/security/SandboxTest.java` — 沙箱安全测试 (SEC-02)
- [ ] `src/test/resources/application-test.yml` — 测试配置文件
- [ ] `src/test/resources/fixtures/` — 测试夹具目录
- [ ] Gradle 依赖配置（JUnit 5, Spring Boot Test, Testcontainers）

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 50ms 性能目标验证 | REXEC-01 | 需要性能压测环境 | 使用 JMeter 或 k6 进行压力测试，验证 P95 < 50ms |
| 内存泄漏验证 | SEC-02 | 需要长期运行监控 | 运行 1000 次脚本编译执行，监控 Metaspace 使用率 |
| 沙箱绕过验证 | SEC-02 | 需要安全专家审查 | 安全专家尝试绕过沙箱，记录成功的方法 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** ✅ Approved (2025-03-26, iteration 3/3)
