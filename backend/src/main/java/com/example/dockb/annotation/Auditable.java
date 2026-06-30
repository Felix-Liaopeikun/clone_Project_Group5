package com.example.dockb.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解。
 *
 * <p>标记在 Controller 方法上，由 {@link com.example.dockb.aspect.AuditAspect} 自动拦截
 * 并记录操作日志到 audit_log 表。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** 操作名称（如"文档上传"、"智能问答"、"模型切换"） */
    String action();

    /** 是否记录请求参数详情（默认不记录，避免泄露敏感信息） */
    boolean logParams() default false;
}
