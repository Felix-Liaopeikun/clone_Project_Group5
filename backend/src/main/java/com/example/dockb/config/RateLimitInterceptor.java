package com.example.dockb.config;

import com.example.dockb.common.Result;
import com.example.dockb.common.ResultCode;
import com.example.dockb.util.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶频率限制拦截器。
 *
 * <p>采用双维度限流：IP 级别 + 用户级别，任一维度触发即返回 429。
 * <p>令牌桶算法：固定的令牌补充速率(refillRate)和容量(burstSize)，拒绝策略为丢弃请求。
 */
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** IP 级别令牌桶 */
    private final ConcurrentHashMap<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();
    /** 用户级别令牌桶 */
    private final ConcurrentHashMap<Long, TokenBucket> userBuckets = new ConcurrentHashMap<>();

    public RateLimitInterceptor(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!properties.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();
        // 白名单放行
        if (properties.getWhitelist() != null) {
            for (String pattern : properties.getWhitelist()) {
                if (pathMatcher.match(pattern, path)) {
                    return true;
                }
            }
        }

        // IP 级别限流
        String ip = getClientIp(request);
        String ipKey = "IP:" + ip;
        TokenBucket ipBucket = ipBuckets.computeIfAbsent(ipKey, k -> newTokenBucket());
        if (!ipBucket.tryConsume()) {
            log.warn("[RateLimit] IP {} 触发频率限制 on {}", ip, path);
            writeRateLimitResponse(response);
            return false;
        }

        // 用户级别限流（仅登录用户）
        Long userId = AuthContext.getUserIdSilent(request);
        if (userId != null) {
            TokenBucket userBucket = userBuckets.computeIfAbsent(userId, k -> newTokenBucket());
            if (!userBucket.tryConsume()) {
                log.warn("[RateLimit] 用户 {} 触发频率限制 on {}", userId, path);
                writeRateLimitResponse(response);
                return false;
            }
        }

        // 定期清理过期桶（概率触发，避免每次都遍历）
        if (Math.random() < 0.01) {
            cleanExpiredBuckets();
        }

        return true;
    }

    private TokenBucket newTokenBucket() {
        return new TokenBucket(
                properties.getBurstSize(),
                properties.getRequestsPerMinute() / 60.0
        );
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws Exception {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> result = Result.fail(ResultCode.RATE_LIMITED);
        response.getWriter().write(objectMapper.writeValueAsString(result));
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

    private void cleanExpiredBuckets() {
        long now = System.currentTimeMillis();
        ipBuckets.entrySet().removeIf(e -> now - e.getValue().lastAccess.get() > 300_000);
        userBuckets.entrySet().removeIf(e -> now - e.getValue().lastAccess.get() > 300_000);
    }

    /**
     * 令牌桶实现：固定速率补充，固定容量。
     * 使用 AtomicLong + Double.doubleToLongBits 替代不存在的 AtomicDouble。
     */
    static class TokenBucket {
        final double capacity;
        final double refillPerMs;
        final AtomicLong tokens;      // 存储 Double.doubleToLongBits 编码值
        final AtomicLong lastAccess;

        TokenBucket(double capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerMs = refillPerSecond / 1000.0;
            this.tokens = new AtomicLong(Double.doubleToLongBits(capacity));
            this.lastAccess = new AtomicLong(System.currentTimeMillis());
        }

        boolean tryConsume() {
            refill();
            double current = Double.longBitsToDouble(tokens.get());
            if (current >= 1.0) {
                return tokens.compareAndSet(
                        Double.doubleToLongBits(current),
                        Double.doubleToLongBits(current - 1.0));
            }
            // 自旋重试一次
            refill();
            current = Double.longBitsToDouble(tokens.get());
            if (current >= 1.0) {
                return tokens.compareAndSet(
                        Double.doubleToLongBits(current),
                        Double.doubleToLongBits(current - 1.0));
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long last = lastAccess.getAndSet(now);
            long elapsed = now - last;
            if (elapsed <= 0) return;
            double current = Double.longBitsToDouble(tokens.get());
            double newTokens = Math.min(capacity, current + elapsed * refillPerMs);
            tokens.set(Double.doubleToLongBits(newTokens));
        }
    }
}
