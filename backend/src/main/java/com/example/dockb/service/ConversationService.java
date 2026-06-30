package com.example.dockb.service;

import com.example.dockb.vo.ConversationVO;

import java.util.List;

/** 对话管理服务 */
public interface ConversationService {
    List<ConversationVO> listConversations(Long userId, boolean isAdmin);
    ConversationVO getConversation(String conversationId, Long userId, boolean isAdmin);
    void deleteConversation(String conversationId, Long userId, boolean isAdmin);
    String exportConversation(String conversationId, String format, Long userId, boolean isAdmin);
}
