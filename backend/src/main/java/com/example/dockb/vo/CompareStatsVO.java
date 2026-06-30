package com.example.dockb.vo;

import lombok.Data;

import java.util.Map;

/**
 * 模型对比统计数据。
 */
@Data
public class CompareStatsVO {

    /** 总对比次数 */
    private long totalComparisons;

    /** 各模型获胜次数 { "MiniMax-M3": 15, "deepseek-chat": 10 } */
    private Map<String, Long> modelWins;

    /** 各模型胜率 { "MiniMax-M3": 60.0, "deepseek-chat": 40.0 } */
    private Map<String, Double> modelWinRate;
}
