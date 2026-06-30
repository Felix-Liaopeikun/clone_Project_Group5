package com.example.dockb.controller;

import com.example.dockb.common.Result;
import com.example.dockb.annotation.RequireRole;
import com.example.dockb.service.EvaluationRunnerService;
import com.example.dockb.vo.EvaluationReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 自动化评测运行器 API */
@Slf4j
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationRunnerController {

    private final EvaluationRunnerService runnerService;

    public EvaluationRunnerController(EvaluationRunnerService runnerService) {
        this.runnerService = runnerService;
    }

    @PostMapping("/run")
    @RequireRole(RequireRole.Role.ADMIN)
    public Result<Map<String, String>> run(@RequestParam(defaultValue = "MiniMax-M3") String model,
                                            @RequestParam(defaultValue = "5") int topK) {
        String reportId = runnerService.runAsync(model, topK);
        log.info("[Eval] Started run reportId={}", reportId);
        return Result.success(Map.of("reportId", reportId));
    }

    @GetMapping("/reports")
    public Result<List<String>> listReports() {
        return Result.success(runnerService.listReports());
    }

    @GetMapping("/reports/{reportId}")
    public Result<EvaluationReportVO> getReport(@PathVariable String reportId) {
        EvaluationReportVO vo = runnerService.getReport(reportId);
        return vo != null ? Result.success(vo) : Result.fail(404, "报告不存在");
    }
}
