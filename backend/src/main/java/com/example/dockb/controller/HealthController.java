package com.example.dockb.controller;

import com.example.dockb.common.Result;
import com.example.dockb.config.CacheConfig;
import com.example.dockb.config.M3Properties;
import com.example.dockb.service.M3Service;
import com.example.dockb.vo.HealthVO;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查（契约 §5.5），结果缓存 30 秒。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final M3Service m3Service;
    private final M3Properties m3Props;

    public HealthController(M3Service m3Service, M3Properties m3Props) {
        this.m3Service = m3Service;
        this.m3Props = m3Props;
    }

    @GetMapping
    @Cacheable(value = CacheConfig.HEALTH_CACHE, unless = "#result == null")
    public Result<HealthVO> health() {
        boolean reachable = m3Props.isKeyConfigured() && m3Service.isReachable(m3Props.getModel());
        return Result.success(new HealthVO("up", reachable, m3Props.getModel()));
    }
}