package com.example.dockb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * QA 请求体（契约 §5.4）。
 */
@Data
public class QaAskRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过 2000 字")
    private String question;

    /** 默认 5，范围 [1, 20]。 */
    private Integer topK;

    /**
     * 可选：指定本次问答使用的模型名称。
     * 若为空，则使用当前激活的默认模型（ModelRegistry.activeModel）。
     * 示例值：MiniMax-M3 / gpt-4o / gpt-3.5-turbo
     */
    private String model;

    /**
     * 可选：多轮对话会话 ID（UUID）。
     * 同一会话内的问答会携带历史上下文，实现多轮对话。
     */
    private String conversationId;
}