package com.example.dockb.service;

import com.example.dockb.vo.EvaluationReportVO;

/** 自动化评测运行器 */
public interface EvaluationRunnerService {
    /** 运行评测，返回报告 ID */
    String runAsync(String model, int topK);
    /** 获取评测报告 */
    EvaluationReportVO getReport(String reportId);
    /** 列出所有评测报告 */
    java.util.List<String> listReports();
}
