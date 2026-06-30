package com.example.dockb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 智能体问答请求。
 */
@Data
public class AgentRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000)
    private String question;

    /** 最大推理步数，默认 5，最大 10 */
    private Integer maxSteps = 5;

    /** 指定使用的 AI 模型 */
    private String model;
}
