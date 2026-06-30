package com.example.dockb.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 多模型对比结果。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompareResultVO {

    private Long id;
    private String question;
    private List<ModelAnswerVO> results;
    private String winner;
    private LocalDateTime createdAt;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelAnswerVO {
        /** 模型名称 */
        private String model;
        /** 模型回答 */
        private String answer;
        /** 引用来源 */
        private List<CitationVO> citations;
        /** 响应延迟（毫秒） */
        private long latencyMs;
        /** 用于投票的标识 */
        private String tag; // "A" or "B" or "C" etc.
    }
}
