package com.example.dockb.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 对话视图对象 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationVO {
    private String conversationId;
    private String title;
    private int messageCount;
    private LocalDateTime lastActivity;
    private List<QaHistoryVO> messages;
}
