package com.example.dockb.controller;

import com.example.dockb.common.Result;
import com.example.dockb.config.CacheConfig;
import com.example.dockb.config.ai.ModelRegistry;
import com.example.dockb.vo.ModelVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型切换 API，模型列表缓存 10 分钟。
 */
@Slf4j
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ModelRegistry modelRegistry;

    public ModelController(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    @GetMapping
    @Cacheable(value = CacheConfig.MODEL_CACHE, unless = "#result == null")
    public Result<List<ModelVO>> listModels() {
        String active = modelRegistry.getActiveModel();
        List<ModelVO> vos = modelRegistry.getModels().stream()
                .map(mi -> {
                    ModelVO vo = new ModelVO();
                    vo.setName(mi.getName());
                    vo.setProvider(mi.getProvider());
                    vo.setDescription(mi.getDescription());
                    vo.setSupportsStream(mi.isSupportsStream());
                    vo.setActive(mi.getName().equals(active));
                    return vo;
                })
                .toList();
        return Result.success(vos);
    }

    @PostMapping("/switch")
    @CacheEvict(value = CacheConfig.MODEL_CACHE, allEntries = true)
    public Result<ModelVO> switchModel(@RequestParam String model) {
        try {
            modelRegistry.switchTo(model);
            log.info("[Model] switched to: {}", model);
            ModelVO vo = new ModelVO();
            ModelRegistry.ModelInfo info = modelRegistry.getActiveModelInfo();
            vo.setName(info.getName());
            vo.setProvider(info.getProvider());
            vo.setDescription(info.getDescription());
            vo.setSupportsStream(info.isSupportsStream());
            vo.setActive(true);
            return Result.success(vo);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, "模型不存在: " + model);
        }
    }

    @GetMapping("/active")
    @Cacheable(value = CacheConfig.MODEL_CACHE, unless = "#result == null")
    public Result<ModelVO> getActive() {
        ModelRegistry.ModelInfo info = modelRegistry.getActiveModelInfo();
        ModelVO vo = new ModelVO();
        vo.setName(info.getName());
        vo.setProvider(info.getProvider());
        vo.setDescription(info.getDescription());
        vo.setSupportsStream(info.isSupportsStream());
        vo.setActive(true);
        return Result.success(vo);
    }
}
