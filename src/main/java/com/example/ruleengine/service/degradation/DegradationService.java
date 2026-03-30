package com.example.ruleengine.service.degradation;

import com.example.ruleengine.model.DecisionRequest;
import com.example.ruleengine.model.DecisionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 降级服务
 * 提供规则执行超时和熔断场景下的降级响应
 */
@Service
public class DegradationService {

    private static final Logger logger = LoggerFactory.getLogger(DegradationService.class);

    /**
     * 超时降级：规则执行超时时返回 PASS 决策
     *
     * @param request 原始决策请求
     * @param t       超时异常
     * @return 降级后的决策响应
     */
    public DecisionResponse getTimeoutFallback(DecisionRequest request, Throwable t) {
        logger.warn("Rule execution timeout for rule: {}, fallback to PASS", request.getRuleId());
        return DecisionResponse.builder()
                .decision("PASS")
                .reason("规则执行超时，降级返回PASS")
                .executionTimeMs(50)
                .build();
    }

    /**
     * 熔断降级：熔断器开启时返回 PASS 决策
     *
     * @param request 原始决策请求
     * @param t       熔断异常
     * @return 降级后的决策响应
     */
    public DecisionResponse getCircuitBreakerFallback(DecisionRequest request, Throwable t) {
        logger.error("Circuit breaker open for rule: {}, fallback to PASS", request.getRuleId());
        return DecisionResponse.builder()
                .decision("PASS")
                .reason("熔断器开启，降级返回PASS")
                .executionTimeMs(0)
                .build();
    }
}
