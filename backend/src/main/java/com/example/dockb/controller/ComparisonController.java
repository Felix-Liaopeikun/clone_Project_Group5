package com.example.dockb.controller;

import com.example.dockb.common.Result;
import com.example.dockb.dto.CompareRequest;
import com.example.dockb.annotation.RequireRole;
import com.example.dockb.service.ComparisonService;
import com.example.dockb.util.AuthContext;
import com.example.dockb.vo.CompareResultVO;
import com.example.dockb.vo.CompareStatsVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 多模型对比 API。
 */
@Slf4j
@RestController
@RequestMapping("/api/qa/compare")
public class ComparisonController {

    private final ComparisonService comparisonService;

    public ComparisonController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    /** 多模型并行对比 */
    @PostMapping
    @RequireRole(RequireRole.Role.USER)
    public Result<CompareResultVO> compare(@RequestBody @Valid CompareRequest req,
                                            HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        boolean isAdmin = AuthContext.isAdmin(request);
        return Result.success(comparisonService.compare(req, userId, isAdmin));
    }

    /** 用户投票 */
    @PostMapping("/{id}/vote")
    @RequireRole(RequireRole.Role.USER)
    public Result<?> vote(@PathVariable Long id,
                           @RequestBody Map<String, String> body,
                           HttpServletRequest request) {
        String winner = body.get("winner");
        Long userId = AuthContext.getUserId(request);
        comparisonService.vote(id, winner, userId);
        return Result.success(Map.of("voted", true));
    }

    /** 对比历史 */
    @GetMapping("/history")
    @RequireRole(RequireRole.Role.USER)
    public Result<?> history(@RequestParam(defaultValue = "1") long page,
                              @RequestParam(defaultValue = "10") long size,
                              HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        return Result.success(comparisonService.history(page, size, userId));
    }

    /** 对比统计 */
    @GetMapping("/stats")
    public Result<CompareStatsVO> stats() {
        return Result.success(comparisonService.stats());
    }
}
