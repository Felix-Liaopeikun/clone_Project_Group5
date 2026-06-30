package com.example.dockb.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置：基于 Caffeine 的多级本地缓存。
 *
 * <p>缓存策略：
 * <ul>
 *   <li>搜索缓存：5 分钟过期，最大 500 条</li>
 *   <li>模型列表缓存：10 分钟过期</li>
 *   <li>分类缓存：10 分钟过期</li>
 *   <li>健康检查缓存：30 秒过期</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SEARCH_CACHE = "searchCache";
    public static final String MODEL_CACHE = "modelCache";
    public static final String CATEGORY_CACHE = "categoryCache";
    public static final String HEALTH_CACHE = "healthCache";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(Arrays.asList(
                buildCache(SEARCH_CACHE, 5, TimeUnit.MINUTES, 500),
                buildCache(MODEL_CACHE, 10, TimeUnit.MINUTES, 10),
                buildCache(CATEGORY_CACHE, 10, TimeUnit.MINUTES, 5),
                buildCache(HEALTH_CACHE, 30, TimeUnit.SECONDS, 2)
        ));
        return manager;
    }

    private Cache buildCache(String name, long duration, TimeUnit unit, int maxSize) {
        return new ConcurrentMapCache(name,
                Caffeine.newBuilder()
                        .expireAfterWrite(duration, unit)
                        .maximumSize(maxSize)
                        .recordStats()
                        .build()
                        .asMap(),
                false);
    }
}
