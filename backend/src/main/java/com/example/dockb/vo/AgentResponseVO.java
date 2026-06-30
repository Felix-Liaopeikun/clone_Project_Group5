package com.example.dockb.vo;

import com.example.dockb.agent.AgentStep;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体问答响应。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponseVO {

    private String question;
    private String finalAnswer;
    private List<CitationVO> citations;
    private List<AgentStep> steps;
    private long totalElapsedMs;
    private int totalSteps;
    private LocalDateTime createdAt;
}
