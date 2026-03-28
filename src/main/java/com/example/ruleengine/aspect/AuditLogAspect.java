package com.example.ruleengine.aspect;

import com.example.ruleengine.annotation.Auditable;
import com.example.ruleengine.service.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * 审计日志 AOP 切面
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String entityType = auditable.entityType();
        String entityId = null;
        String operator = "system";
        String operatorIp = null;
        String requestId = null;

        // 提取请求上下文
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            operatorIp = getClientIp(request);
            requestId = request.getHeader("X-Request-ID");
            operator = request.getHeader("X-Operator");
            if (operator == null || operator.isEmpty()) {
                operator = "system";
            }
        }

        // 提取实体ID（SpEL）
        if (!auditable.entityIdExpression().isEmpty()) {
            entityId = evaluateSpel(joinPoint, auditable.entityIdExpression());
        }

        // 构建操作详情
        Map<String, Object> details = new HashMap<>();
        details.put("method", joinPoint.getSignature().getName());
        details.put("className", joinPoint.getTarget().getClass().getSimpleName());

        String status = "SUCCESS";
        String errorMessage = null;
        Object result = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            status = "FAILED";
            errorMessage = e.getMessage();
            log.error("审计方法执行失败: {}", auditable.event(), e);
            throw e;
        } finally {
            // 异步记录审计日志
            if ("SUCCESS".equals(status)) {
                auditLogService.logOperationWithContext(
                        entityType,
                        entityId != null ? entityId : "unknown",
                        auditable.event(),
                        operator,
                        details,
                        operatorIp,
                        requestId
                );
            } else {
                auditLogService.logFailure(
                        entityType,
                        entityId != null ? entityId : "unknown",
                        auditable.event(),
                        operator,
                        errorMessage
                );
            }
        }
    }

    /**
     * 评估 SpEL 表达式
     */
    private String evaluateSpel(ProceedingJoinPoint joinPoint, String expressionString) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("args", joinPoint.getArgs());

            // 设置参数名映射（简单实现，实际可以使用 ParameterNameDiscoverer）
            for (int i = 0; i < joinPoint.getArgs().length; i++) {
                context.setVariable("arg" + i, joinPoint.getArgs()[i]);
                context.setVariable("p" + i, joinPoint.getArgs()[i]);
                // 也支持通过索引访问：#p0, #p1 等
            }

            Expression expression = parser.parseExpression(expressionString);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("SpEL 表达式评估失败: {}", expressionString, e);
            return null;
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
