package com.example.dockb.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * QA 历史列表条目 VO（分页用）。
 */
@Data
public class QaHistoryVO {

    private Long id;
    private String question;
    private String answer;
    private List<CitationVO> citations;

    private String conversationId;

    private Integer rating;
    private Boolean useful;
    private String feedback;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}