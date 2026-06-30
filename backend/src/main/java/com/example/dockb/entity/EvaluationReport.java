package com.example.dockb.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 评测报告实体 */
@Data
@TableName("evaluation_report")
public class EvaluationReport {

    @TableId
    private String id;
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
    private String detailsJson;
    private LocalDateTime completedAt;
}
