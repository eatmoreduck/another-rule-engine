// =============================================================================
// K6 决策链路压测脚本（阶段 6，50ms SLA 验证）
//
// 两层场景：
//   1. cold-decide —— POST /api/v1/decide 直传脚本：每次请求冷编译 Groovy，
//      衡量"冷启动决策"路径（SLA 之外的上界参考，阈值默认放宽至 250ms）
//   2. warm-decide —— POST /api/v1/decide/{ruleKey} cache-aware 决策：
//      快照缓存命中路径，50ms SLA 的真正载体（阈值 p95 < 50ms）
//
// 运行方式见 deploy/k6/README.md。常用环境变量：
//   BASE_URL   决策服务地址（默认 http://localhost:18081，compose 栈映射端口）
//   TOKEN      Sa-Token 值（决策服务开启认证时必填；关闭认证时忽略）
//   RULE_KEY   warm 场景使用的规则 key（默认 k6-warm-rule，需先在库中存在）
//   COLD_VUS / WARM_VUS / DURATION  两场景的虚拟用户数与持续时长
// =============================================================================
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18081';
const TOKEN = __ENV.TOKEN || '';
const RULE_KEY = __ENV.RULE_KEY || 'k6-warm-rule';
const COLD_VUS = Number(__ENV.COLD_VUS || 5);
const WARM_VUS = Number(__ENV.WARM_VUS || 20);
const DURATION = __ENV.DURATION || '2m';
// SLA 阈值可通过环境变量覆盖（如 CI 上先跑低门槛）
const WARM_P95_MS = Number(__ENV.WARM_P95_MS || 50);
const COLD_P95_MS = Number(__ENV.COLD_P95_MS || 250);
const ERROR_RATE = Number(__ENV.ERROR_RATE || 0.001); // < 0.1%

// 直传决策脚本（与后端契约测试同款语句，Groovy 沙箱白名单内）
const COLD_SCRIPT = "return features.order_amount > 1000 ? 'REJECT' : 'PASS'";

// 自定义指标：分层观察两场景的端到端耗时与服务端执行耗时（executionTimeMs）
// 说明：k6 对带 tag 的内建 http_req_duration 子指标在 handleSummary 中序列化为 0，
// 故端到端分位数用自定义 Trend 承载（thresholds 也挂其上，行为可靠）
const e2eWarm = new Trend('warm_e2e_ms');
const e2eCold = new Trend('cold_e2e_ms');
const serverExecWarm = new Trend('warm_server_exec_ms');
const serverExecCold = new Trend('cold_server_exec_ms');
const decisionRejectRate = new Rate('decision_reject_rate');
const timeoutCounter = new Counter('decision_timeout_total');

export const options = {
  scenarios: {
    'cold-decide': {
      executor: 'constant-vus',
      vus: COLD_VUS,
      duration: DURATION,
      tags: { scenario: 'cold' },
      exec: 'coldDecide',
    },
    'warm-decide': {
      executor: 'constant-vus',
      vus: WARM_VUS,
      duration: DURATION,
      startTime: '30s', // 冷场景先跑 30s，避免冷启动 JIT 干扰 warm 场景起步
      tags: { scenario: 'warm' },
      exec: 'warmDecide',
    },
  },
  thresholds: {
    // 50ms SLA：cache-aware 决策路径端到端 p95（自定义 Trend，见上说明）
    warm_e2e_ms: [`p(95)<${WARM_P95_MS}`],
    // 冷编译路径：默认放宽为上界参考（不作为 SLA 判定）
    cold_e2e_ms: [`p(95)<${COLD_P95_MS}`],
    // 错误率 < 0.1%（HTTP 层失败，按 scenario tag 分开计算）
    'http_req_failed{scenario:warm}': [`rate<${ERROR_RATE}`],
    'http_req_failed{scenario:cold}': [`rate<${ERROR_RATE}`],
    // 整体压测吞吐量保护：总请求过少视为栈未就绪
    http_reqs: ['count>100'],
  },
  // 压测期保持连接复用，贴近网关长连接形态
  noConnectionReuse: false,
  userAgent: 'k6-ruleengine/1.0',
};

const PARAMS = {
  headers: {
    'Content-Type': 'application/json',
    // Sa-Token 约定：Authorization 头裸读，无 Bearer 前缀
    ...(TOKEN ? { Authorization: TOKEN } : {}),
  },
  timeout: '5s',
};

function assertDecision(res, scenario) {
  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    '决策响应结构': (r) => {
      try {
        const body = r.json();
        return body && typeof body.decision === 'string' && typeof body.executionTimeMs === 'number';
      } catch (e) {
        return false;
      }
    },
  });
  if (!ok) {
    return;
  }
  const body = res.json();
  decisionRejectRate.add(body.decision === 'REJECT');
  if (body.timeout) {
    timeoutCounter.add(1);
  }
  if (scenario === 'warm') {
    e2eWarm.add(res.timings.duration);
    serverExecWarm.add(body.executionTimeMs);
  } else {
    e2eCold.add(res.timings.duration);
    serverExecCold.add(body.executionTimeMs);
  }
}

/** 场景 1：直传脚本冷编译决策 */
export function coldDecide() {
  group('cold: POST /api/v1/decide', () => {
    const payload = JSON.stringify({
      ruleId: 'k6-cold-rule',
      script: COLD_SCRIPT,
      features: { order_amount: Math.floor(Math.random() * 2000), userId: `k6-${__VU}` },
      timeoutMs: 200,
    });
    const res = http.post(`${BASE_URL}/api/v1/decide`, payload, PARAMS);
    assertDecision(res, 'cold');
  });
  sleep(0.2);
}

/** 场景 2：cache-aware 决策（快照缓存命中路径） */
export function warmDecide() {
  group('warm: POST /api/v1/decide/{ruleKey}', () => {
    const payload = JSON.stringify({
      order_amount: Math.floor(Math.random() * 2000),
      userId: `k6-${__VU}`,
    });
    const res = http.post(`${BASE_URL}/api/v1/decide/${RULE_KEY}`, payload, PARAMS);
    // warm 场景要求规则已存在（README 有准备步骤）；404/400 会计入 http_req_failed
    assertDecision(res, 'warm');
  });
  sleep(0.1);
}

export function handleSummary(data) {
  const lines = [
    '',
    '======== 决策链路压测摘要 ========',
    `warm 端到端 p95: ${fmtTrend(data.metrics.warm_e2e_ms)}（SLA 门槛 ${WARM_P95_MS}ms）`,
    `cold 端到端 p95: ${fmtTrend(data.metrics.cold_e2e_ms)}（参考门槛 ${COLD_P95_MS}ms）`,
    `warm 服务端执行 p95: ${fmtTrend(data.metrics.warm_server_exec_ms)}`,
    `cold 服务端执行 p95: ${fmtTrend(data.metrics.cold_server_exec_ms)}`,
    `REJECT 比例: ${data.metrics.decision_reject_rate ? (data.metrics.decision_reject_rate.values.rate * 100).toFixed(2) + '%' : 'n/a'}`,
    `决策超时次数: ${data.metrics.decision_timeout_total ? data.metrics.decision_timeout_total.values.count : 0}`,
    `总请求: ${data.metrics.http_reqs.values.count}`,
    '==================================',
    '',
  ];
  // 默认详细 summary（含各指标分位 + thresholds ✓/✗）+ 自定义摘要
  return { stdout: textSummary(data, { indent: ' ', enableColors: false }) + lines.join('\n') };
}

function fmtTrend(metric) {
  if (!metric) return 'n/a';
  return `${metric.values['p(95)'].toFixed(2)}ms`;
}
