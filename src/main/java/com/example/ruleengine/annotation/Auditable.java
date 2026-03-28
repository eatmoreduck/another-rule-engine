package com.example.ruleengine.annotation;

import com.example.ruleengine.constants.AuditEvent;

import java.lang.annotation.*;

/**
 * 审计注解，用于标记需要审计的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /**
     * 审计事件类型
     */
    AuditEvent event();

    /**
     * 实体类型
     */
    String entityType();

    /**
     * 实体ID表达式（SpEL）
     */
    String entityIdExpression() default "";

    /**
     * 操作详情表达式（SpEL）
     */
    String detailExpression() default "";
}
