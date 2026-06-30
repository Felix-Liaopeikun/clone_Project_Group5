package com.example.dockb.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 自动化评测报告 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationReportVO {
    private String reportId;
    private String model;
    private int totalQuestions;
    private double avgRecall;
    private double avgAccuracy;
    private double avgCompleteness;
    private double avgRelevance;
    private double avgClarity;
    private double avgOverall;
    private double avgCitationAccuracy;
    private double avgLatencyMs;
    private List<EvalResult> details;
    private LocalDateTime completedAt;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EvalResult {
        private String questionId;
        private String question;
        private String expectedAnswer;
        private String actualAnswer;
        private double recall;
        private Map<String, Double> scores;
        private double citationAccuracy;
        private long latencyMs;
    }
}
