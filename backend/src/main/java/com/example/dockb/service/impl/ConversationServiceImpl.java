package com.example.dockb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dockb.common.BizException;
import com.example.dockb.common.ResultCode;
import com.example.dockb.entity.QaHistory;
import com.example.dockb.mapper.QaHistoryMapper;
import com.example.dockb.service.ConversationService;
import com.example.dockb.vo.CitationVO;
import com.example.dockb.vo.ConversationVO;
import com.example.dockb.vo.QaHistoryVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    private final QaHistoryMapper qaHistoryMapper;
    private final ObjectMapper objectMapper;

    public ConversationServiceImpl(QaHistoryMapper qaHistoryMapper, ObjectMapper objectMapper) {
        this.qaHistoryMapper = qaHistoryMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ConversationVO> listConversations(Long userId, boolean isAdmin) {
        LambdaQueryWrapper<QaHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(QaHistory::getConversationId)
               .ne(QaHistory::getConversationId, "");
        if (!isAdmin) {
            if (userId != null) {
                wrapper.and(w -> w.isNull(QaHistory::getOwnerId).or().eq(QaHistory::getOwnerId, userId));
            } else {
                wrapper.isNull(QaHistory::getOwnerId);
            }
        }
        wrapper.orderByDesc(QaHistory::getCreatedAt);
        List<QaHistory> all = qaHistoryMapper.selectList(wrapper);

        Map<String, List<QaHistory>> grouped = all.stream()
                .collect(Collectors.groupingBy(QaHistory::getConversationId, LinkedHashMap::new, Collectors.toList()));

        List<ConversationVO> result = new ArrayList<>();
        for (Map.Entry<String, List<QaHistory>> entry : grouped.entrySet()) {
            List<QaHistory> msgs = entry.getValue();
            QaHistory first = msgs.get(msgs.size() - 1);
            ConversationVO vo = new ConversationVO();
            vo.setConversationId(entry.getKey());
            vo.setTitle(truncate(first.getQuestion(), 50));
            vo.setMessageCount(msgs.size());
            vo.setLastActivity(msgs.get(0).getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    @Override
    public ConversationVO getConversation(String conversationId, Long userId, boolean isAdmin) {
        LambdaQueryWrapper<QaHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaHistory::getConversationId, conversationId);
        if (!isAdmin) {
            if (userId != null) {
                wrapper.and(w -> w.isNull(QaHistory::getOwnerId).or().eq(QaHistory::getOwnerId, userId));
            } else {
                wrapper.isNull(QaHistory::getOwnerId);
            }
        }
        wrapper.orderByAsc(QaHistory::getCreatedAt);
        List<QaHistory> msgs = qaHistoryMapper.selectList(wrapper);
        if (msgs.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "对话不存在");
        }

        ConversationVO vo = new ConversationVO();
        vo.setConversationId(conversationId);
        vo.setTitle(truncate(msgs.get(0).getQuestion(), 50));
        vo.setMessageCount(msgs.size());
        vo.setLastActivity(msgs.get(msgs.size() - 1).getCreatedAt());
        vo.setMessages(msgs.stream().map(this::toVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(String conversationId, Long userId, boolean isAdmin) {
        LambdaQueryWrapper<QaHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaHistory::getConversationId, conversationId);
        if (!isAdmin) {
            if (userId != null) {
                wrapper.eq(QaHistory::getOwnerId, userId);
            } else {
                throw new BizException(ResultCode.FORBIDDEN, "请先登录");
            }
        }
        int deleted = qaHistoryMapper.delete(wrapper);
        log.info("[Conversation] deleted conversation {}, {} records", conversationId, deleted);
    }

    @Override
    public String exportConversation(String conversationId, String format, Long userId, boolean isAdmin) {
        ConversationVO conv = getConversation(conversationId, userId, isAdmin);
        if (conv.getMessages() == null || conv.getMessages().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# 对话导出\n\n");
        sb.append("**会话ID**: ").append(conversationId).append("\n");
        sb.append("**消息数**: ").append(conv.getMessageCount()).append("\n");
        sb.append("**导出时间**: ").append(LocalDateTime.now()).append("\n\n---\n\n");

        for (int i = 0; i < conv.getMessages().size(); i++) {
            QaHistoryVO msg = conv.getMessages().get(i);
            sb.append("## Q").append(i + 1).append(": ").append(msg.getQuestion()).append("\n\n");
            sb.append(msg.getAnswer()).append("\n\n");
            if (msg.getCitations() != null && !msg.getCitations().isEmpty()) {
                sb.append("**引用来源**:\n");
                for (CitationVO c : msg.getCitations()) {
                    sb.append("- ").append(c.getTitle())
                      .append(" (得分: ").append(String.format("%.2f", c.getScore())).append(")\n");
                    sb.append("  > ").append(c.getSnippet()).append("\n");
                }
                sb.append("\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    private QaHistoryVO toVO(QaHistory h) {
        QaHistoryVO vo = new QaHistoryVO();
        vo.setId(h.getId());
        vo.setQuestion(h.getQuestion());
        vo.setAnswer(h.getAnswer());
        try {
            String c = h.getCitations();
            if (c == null || c.isBlank() || "null".equalsIgnoreCase(c)) {
                vo.setCitations(Collections.emptyList());
            } else {
                vo.setCitations(objectMapper.readValue(c, new TypeReference<List<CitationVO>>() {}));
            }
        } catch (Exception e) {
            vo.setCitations(Collections.emptyList());
        }
        vo.setConversationId(h.getConversationId());
        vo.setRating(h.getRating());
        vo.setUseful(h.getUseful());
        vo.setCreatedAt(h.getCreatedAt());
        return vo;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
