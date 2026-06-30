package com.example.dockb.config;

import com.example.dockb.common.Result;
import com.example.dockb.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * 无状态 CSRF Token 过滤器。
 *
 * <p>对状态变更请求（POST/PUT/DELETE）校验 X-CSRF-Token 请求头。
 * <p>Token 由服务端基于 sessionId + secret 的 HMAC-SHA256 生成，无状态校验。
 * <p>GET 请求在响应头中下发 CSRF Token。
 */
@Slf4j
public class CsrfTokenFilter extends OncePerRequestFilter {

    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final String CSRF_SESSION_ID = "X-CSRF-Session-Id";
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/auth/login", "/api/auth/register", "/api/health", "/api/models"
    );

    private final String secret;
    private final boolean enabled;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CsrfTokenFilter(String secret, boolean enabled) {
        this.secret = secret;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        String path = request.getRequestURI();

        // 排除路径直接放行
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 生成或获取 sessionId
        String sessionId = request.getHeader(CSRF_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = generateSessionId();
        }

        // 生成 CSRF Token
        String expectedToken = hmacSha256(sessionId, secret);
        // 在响应头中下发 sessionId 和 token，方便前端获取
        response.setHeader(CSRF_SESSION_ID, sessionId);
        response.setHeader(CSRF_HEADER, expectedToken);

        // 仅对状态变更请求校验
        if ("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)) {

            String actualToken = request.getHeader(CSRF_HEADER);
            if (actualToken == null || !expectedToken.equals(actualToken)) {
                log.warn("[CSRF] Token 校验失败: path={}, expected={}, actual={}",
                        path, expectedToken.substring(0, 8) + "...",
                        actualToken == null ? "null" : actualToken.substring(0, Math.min(8, actualToken.length())) + "...");
                writeCsrfError(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String generateSessionId() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec spec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(spec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            log.error("[CSRF] HMAC 计算失败", e);
            return "";
        }
    }

    private void writeCsrfError(HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> result = Result.fail(ResultCode.CSRF_INVALID);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
