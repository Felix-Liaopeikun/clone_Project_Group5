package com.example.dockb.util;

import com.example.dockb.config.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;

import java.util.Optional;

/**
 * 当前登录用户上下文工具。
 *
 * <p>在 Controller 方法参数上加 {@code @CurrentUser Long userId} 即可注入当前用户 ID。
 *
 * <p>示例：
 * <pre>
 * &#64;PostMapping("/upload")
 * public Result&lt;...&gt; upload(&#64;CurrentUser Long userId, ...) { ... }
 * </pre>
 */
public class AuthContext {

    /** 从请求 attribute 中提取当前用户 ID。 */
    public static Long getUserId(HttpServletRequest request) {
        Object val = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (val instanceof Long) return (Long) val;
        if (val instanceof Integer) return ((Integer) val).longValue();
        return null;
    }

    public static String getUsername(HttpServletRequest request) {
        Object val = request.getAttribute(JwtAuthFilter.ATTR_USERNAME);
        return val instanceof String ? (String) val : null;
    }

    public static String getRole(HttpServletRequest request) {
        Object val = request.getAttribute(JwtAuthFilter.ATTR_ROLE);
        return val instanceof String ? (String) val : null;
    }

    public static boolean isAdmin(HttpServletRequest request) {
        return "ADMIN".equals(getRole(request));
    }

    /**
     * 解析 @CurrentUser 参数。
     */
    public static Object resolveCurrentUser(MethodParameter param, HttpServletRequest request) {
        Class<?> type = param.getParameterType();
        if (type == Long.class || type == long.class) {
            return getUserId(request);
        }
        if (type == String.class) {
            if (param.hasParameterAnnotation(Username.class)) {
                return getUsername(request);
            }
            return getRole(request);
        }
        return null;
    }

    /**
     * 从当前请求中静默获取用户 ID（不抛异常，用于审计日志等非主流程场景）。
     */
    public static Long getUserIdSilent(HttpServletRequest request) {
        try {
            return getUserId(request);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从当前请求中静默获取用户名。
     */
    public static String getUsernameSilent(HttpServletRequest request) {
        try {
            return getUsername(request);
        } catch (Exception e) {
            return null;
        }
    }

    /** 标注在 String 参数上，注入用户名（而非 role）。用法：&#64;CurrentUser &#64;AuthContext.Username String name */
    @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    public @interface Username {}
}
