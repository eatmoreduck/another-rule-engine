# Phase 1: 核心规则执行引擎 - Discussion Log (Assumptions Mode)

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions captured in CONTEXT.md — this log preserves the analysis.

**Date:** 2025-03-26
**Phase:** 01-core-engine
**Mode:** assumptions
**Areas analyzed:** 项目结构与构建, Groovy 脚本执行引擎, 安全防护, 性能与稳定性, 特征获取策略, API 设计

## Assumptions Presented

### 项目结构与构建
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| 使用 Gradle 8.5+ 作为构建工具 | Confident | PROJECT.md 明确技术栈；研究文档推荐 |
| 使用标准 Spring Boot 3.3.x 多模块结构 | Confident | 研究文档推荐；行业标准 |
| Java 21 LTS + Groovy 4.0.x/5.x | Confident | 研究文档明确推荐 |

### Groovy 脚本执行引擎
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| 使用 GroovyShell 动态编译执行 | Confident | PROJECT.md 明确要求；行业标准 |
| ConcurrentHashMap 脚本缓存 | Confident | PITFALLS.md 强调防止内存泄漏 |
| 单例 GroovyClassLoader | Confident | PITFALLS.md 明确要求 |
| CompilerConfiguration PARALLEL_PARSE | Confident | 研究文档性能优化建议 |

### 安全防护
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| CompilerConfiguration 沙箱 | Confident | PITFALLS.md 防止代码注入 |
| SecurityManager 白名单 | Confident | PITFALLS.md 安全要求 |
| 独立 ClassLoader 执行 | Confident | PITFALLS.md 安全最佳实践 |

### 性能与稳定性
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Resilience4j 超时控制 50ms | Confident | PITFALLS.md 防止服务雪崩；PROJECT.md 性能要求 |
| 独立线程池隔离 | Confident | PITFALLS.md 稳定性要求 |
| Caffeine 本地缓存 | Confident | 研究文档推荐；Phase 1 范围 |

### 特征获取策略
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| 入参 → 外部降级 → 默认值 | Confident | PROJECT.md 明确要求 |
| 特征获取超时降级 | Confident | PITFALLS.md 性能要求 |

### API 设计
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| POST /api/v1/decide | Confident | RESTful 标准；风控场景需求 |
| < 50ms 响应时间 | Confident | PROJECT.md 核心价值要求 |

## Corrections Made

No corrections — all assumptions confirmed.

## Auto-Resolved

N/A — all assumptions were Confident.

## External Research

N/A — project documentation and research provided sufficient evidence.
