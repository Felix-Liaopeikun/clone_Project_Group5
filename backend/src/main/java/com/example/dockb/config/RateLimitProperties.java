package com.example.dockb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 频率限制配置属性。
 *
 * <p>支持 IP 级别和用户级别的令牌桶限流，可配置白名单路径。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** 是否启用频率限制 */
    private boolean enabled = true;

    /** 每分钟允许的请求数 */
    private int requestsPerMinute = 60;

    /** 突发容量（令牌桶最大值） */
    private int burstSize = 10;

    /** 白名单路径（不限制频率），支持 Ant 风格匹配 */
    private List<String> whitelist = List.of("/api/health", "/api/models");
}
