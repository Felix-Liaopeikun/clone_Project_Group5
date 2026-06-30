package com.example.dockb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 多模型对比请求。
 */
@Data
public class CompareRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000)
    private String question;

    @NotEmpty(message = "至少选择 2 个模型")
    @Size(min = 2, max = 4, message = "对比模型数量需在 2~4 之间")
    private List<String> models;

    private Integer topK = 5;

    private String conversationId;
}
