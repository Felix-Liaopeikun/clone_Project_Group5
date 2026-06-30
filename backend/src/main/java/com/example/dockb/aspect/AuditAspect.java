package com.example.dockb.aspect;

import com.example.dockb.annotation.Auditable;
import com.example.dockb.entity.AuditLog;
import com.example.dockb.mapper.AuditLogMapper;
import com.example.dockb.util.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 操作审计 AOP 切面。
 *
 * <p>拦截标记了 {@link Auditable} 注解的 Controller 方法，
 * 异步记录操作日志到 audit_log 表（不阻塞主流程）。
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final AuditLogMapper auditLogMapper;

    public AuditAspect(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        AuditLog audit = new AuditLog();
        audit.setAction(auditable.action());
        audit.setCreatedAt(LocalDateTime.now());

        // 获取当前用户信息
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                audit.setSourceIp(getClientIp(request));
                audit.setUserAgent(request.getHeader("User-Agent"));
                Long userId = AuthContext.getUserIdSilent(request);
                audit.setUserId(userId);
                audit.setUsername(AuthContext.getUsernameSilent(request));
            }
        } catch (Exception e) {
            log.debug("[Audit] 获取用户信息失败: {}", e.getMessage());
        }

        // 记录请求参数（仅当注解允许时）
        if (auditable.logParams()) {
            try {
                Object[] args = joinPoint.getArgs();
                if (args != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < args.length; i++) {
                        if (args[i] instanceof HttpServletRequest
                                || args[i] instanceof jakarta.servlet.http.HttpServletResponse) {
                            continue;
                        }
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(args[i] == null ? "null" : args[i].toString());
                    }
                    String detail = sb.toString();
                    audit.setDetail(detail.length() > 500 ? detail.substring(0, 500) : detail);
                }
            } catch (Exception e) {
                log.debug("[Audit] 序列化参数失败: {}", e.getMessage());
            }
        }

        Object result;
        try {
            result = joinPoint.proceed();
            audit.setStatus("SUCCESS");
        } catch (Throwable t) {
            audit.setStatus("FAIL");
            String msg = t.getMessage();
            audit.setDetail(msg == null ? "unknown error" : msg.length() > 500 ? msg.substring(0, 500) : msg);
            throw t;
        } finally {
            // 异步写入审计日志，不阻塞主流程
            AuditLog finalAudit = audit;
            CompletableFuture.runAsync(() -> {
                try {
                    auditLogMapper.insert(finalAudit);
                } catch (Exception e) {
                    log.warn("[Audit] 写入审计日志失败: {}", e.getMessage());
                }
            });
        }

        return result;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
