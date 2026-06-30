package com.example.dockb.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.dockb.client.dto.QaResult;
import com.example.dockb.client.dto.RankedHit;
import com.example.dockb.common.BizException;
import com.example.dockb.common.PageResult;
import com.example.dockb.common.ResultCode;
import com.example.dockb.dto.EvaluateRequest;
import com.example.dockb.config.AppProperties;
import reactor.core.publisher.Flux;
import com.example.dockb.entity.Document;
import com.example.dockb.entity.DocumentChunk;
import com.example.dockb.entity.QaHistory;
import com.example.dockb.mapper.DocumentChunkMapper;
import com.example.dockb.mapper.DocumentMapper;
import com.example.dockb.mapper.QaHistoryMapper;
import com.example.dockb.service.M3Service;
import com.example.dockb.service.QaService;
import com.example.dockb.util.SnippetUtil;
import com.example.dockb.vo.CitationVO;
import com.example.dockb.vo.QaAnswerVO;
import com.example.dockb.vo.QaHistoryVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QaServiceImpl implements QaService {

    private final DocumentChunkMapper chunkMapper;
    private final DocumentMapper documentMapper;
    private final QaHistoryMapper qaHistoryMapper;
    private final M3Service m3Service;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public QaServiceImpl(DocumentChunkMapper chunkMapper,
                         DocumentMapper documentMapper,
                         QaHistoryMapper qaHistoryMapper,
                         M3Service m3Service,
                         AppProperties appProperties,
                         ObjectMapper objectMapper) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.qaHistoryMapper = qaHistoryMapper;
        this.m3Service = m3Service;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaAnswerVO ask(String question, Integer topK, String model, String conversationId, Long userId, boolean isAdmin) {
        if (question == null || question.isBlank()) {
            throw new BizException(ResultCode.QUESTION_EMPTY);
        }
        int k = topK == null ? 5 : topK;
        if (k <= 0) k = 5;
        if (k > 20) k = 20;

        // 0) 多轮对话：查询同一会话的历史上下文
        String conversationContext = buildConversationContext(conversationId, userId);
        String enrichedQuestion = conversationContext.isEmpty()
                ? question.trim()
                : conversationContext + "\n\n当前问题：" + question.trim();

        // 1) 候选 chunks（权限过滤）
        List<DocumentChunk> allCandidates = searchVisibleChunks(enrichedQuestion, k * 6, userId, isAdmin);
        if (allCandidates.isEmpty()) {
            return saveAndReturn(question, "知识库中暂无相关资料。", Collections.emptyList(), userId, conversationId);
        }

        // 2) 重排
        List<String> texts = allCandidates.stream()
                .map(DocumentChunk::getContent)
                .collect(Collectors.toList());
        List<RankedHit> ranked = m3Service.rerankWithFallback(question, texts, model);

        // 3) 选取 top K
        List<Integer> picked = new ArrayList<>();
        if (ranked.isEmpty()) {
            for (int i = 0; i < allCandidates.size() && picked.size() < k; i++) {
                picked.add(i);
            }
        } else {
            for (RankedHit r : ranked) {
                if (r.getIndex() < 0 || r.getIndex() >= allCandidates.size()) continue;
                picked.add(r.getIndex());
                if (picked.size() >= k) break;
            }
        }

        // 4) 拼上下文
        List<String> context = new ArrayList<>();
        Map<Integer, Double> scoreMap = new HashMap<>();
        for (int i = 0; i < picked.size(); i++) {
            int idx = picked.get(i);
            context.add(texts.get(idx));
            scoreMap.put(i, scoreAt(ranked, idx));
        }

        // 5) 调 M3 问答（带降级）
        QaResult result = m3Service.answerWithFallback(question, context, model);

        // 6) 组装 citations
        Map<Long, Document> docMap = lookupDocs(picked, allCandidates);
        List<CitationVO> citations = new ArrayList<>();
        if (result.getCitations() != null) {
            for (QaResult.Citation c : result.getCitations()) {
                int idx = c.getIndex();
                if (idx < 0 || idx >= context.size()) continue;
                DocumentChunk ch = allCandidates.get(picked.get(idx));
                Document d = docMap.get(ch.getDocumentId());
                CitationVO vo = new CitationVO();
                vo.setDocumentId(ch.getDocumentId());
                vo.setTitle(d == null ? null : d.getTitle());
                vo.setChunkId(ch.getId());
                String snip = c.getSnippet() == null || c.getSnippet().isBlank()
                        ? SnippetUtil.snippet(texts.get(picked.get(idx)), question, appProperties.getSearch().getSnippetRadius())
                        : c.getSnippet();
                vo.setSnippet(snip);
                vo.setScore(scoreMap.getOrDefault(idx, 0d));
                citations.add(vo);
            }
        }
        if (citations.isEmpty()) {
            for (int i = 0; i < picked.size(); i++) {
                int idx = picked.get(i);
                DocumentChunk ch = allCandidates.get(idx);
                Document d = docMap.get(ch.getDocumentId());
                CitationVO vo = new CitationVO();
                vo.setDocumentId(ch.getDocumentId());
                vo.setTitle(d == null ? null : d.getTitle());
                vo.setChunkId(ch.getId());
                vo.setSnippet(SnippetUtil.snippet(texts.get(idx), question, appProperties.getSearch().getSnippetRadius()));
                vo.setScore(scoreMap.getOrDefault(i, 0d));
                citations.add(vo);
            }
        }

        return saveAndReturn(question, result.getAnswer() == null ? "" : result.getAnswer(), citations, userId, conversationId);
    }

    @Override
    public PageResult<QaHistoryVO> history(long page, long size, Long userId, boolean isAdmin) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;

        LambdaQueryWrapper<QaHistory> wrapper = new LambdaQueryWrapper<>();
        // ADMIN 可见所有；普通用户只看自己和匿名的
        if (!isAdmin) {
            if (userId != null) {
                wrapper.and(w -> w.isNull(QaHistory::getOwnerId).or().eq(QaHistory::getOwnerId, userId));
            } else {
                wrapper.isNull(QaHistory::getOwnerId);
            }
        }
        wrapper.orderByDesc(QaHistory::getCreatedAt);

        Page<QaHistory> result = qaHistoryMapper.selectPage(new Page<>(page, size), wrapper);
        List<QaHistoryVO> list = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), page, size);
    }

    @Override
    public Flux<String> askStream(String question, Integer topK, String model, String conversationId, Long userId, boolean isAdmin) {
        int k = topK == null ? 5 : Math.min(Math.max(topK, 1), 20);
        // 多轮对话：将历史上下文拼入检索
        String conversationContext = buildConversationContext(conversationId, userId);
        String enrichedQuestion = conversationContext.isEmpty()
                ? question.trim()
                : conversationContext + "\n\n当前问题：" + question.trim();
        List<String> context = buildContext(enrichedQuestion, k, userId, isAdmin);
        return m3Service.answerStream(question, context, model);
    }

    @Override
    public List<String> buildContext(String question, Integer topK, Long userId, boolean isAdmin) {
        if (question == null || question.isBlank()) {
            return Collections.emptyList();
        }
        int k = (topK == null || topK <= 0) ? 5 : Math.min(topK, 20);

        // 权限过滤
        List<DocumentChunk> candidates = searchVisibleChunks(question.trim(), k, userId, isAdmin);
        return candidates.stream()
                .limit(k)
                .map(DocumentChunk::getContent)
                .collect(Collectors.toList());
    }

    @Override
    public void saveHistoryAsync(String question, String answer, Long ownerId, String conversationId) {
        if (question == null && answer == null) {
            return;
        }
        try {
            QaHistory h = new QaHistory();
            h.setQuestion(question == null ? "" : question);
            h.setAnswer(answer == null ? "" : answer);
            h.setCitations("[]");
            h.setOwnerId(ownerId);
            h.setConversationId(conversationId);
            qaHistoryMapper.insert(h);
            log.info("[QaService] async save history id={}, ownerId={}", h.getId(), ownerId);
        } catch (Exception e) {
            log.warn("[QaService] saveHistoryAsync failed: {}", e.getMessage());
        }
    }

    // ----------------------------- 私有方法 -----------------------------

    /**
     * 权限感知的 chunk 搜索。
     * 优先使用 MySQL FULLTEXT 全文搜索，失败时退化为 LIKE 查询。
     */
    private List<DocumentChunk> searchVisibleChunks(String keyword, int limit, Long userId, boolean isAdmin) {
        int maxCandidates = appProperties.getSearch().getMaxCandidates();
        List<DocumentChunk> candidates;

        // 1) 优先尝试 FULLTEXT 全文搜索
        try {
            candidates = chunkMapper.searchFulltext(keyword, maxCandidates);
        } catch (Exception e) {
            log.debug("[QaService] FULLTEXT search failed, falling back to LIKE: {}", e.getMessage());
            candidates = null;
        }

        // 2) FULLTEXT 无结果时回退至 LIKE 查询
        if (candidates == null || candidates.isEmpty()) {
            candidates = chunkMapper.selectList(
                    new LambdaQueryWrapper<DocumentChunk>()
                            .like(DocumentChunk::getContent, keyword)
                            .last("LIMIT " + maxCandidates));
        }
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 取关联文档做权限过滤
        Set<Long> docIds = candidates.stream()
                .map(DocumentChunk::getDocumentId)
                .collect(Collectors.toSet());
        Map<Long, Document> docMap = new HashMap<>();
        if (!docIds.isEmpty()) {
            documentMapper.selectBatchIds(docIds).forEach(d -> docMap.put(d.getId(), d));
        }

        Set<Long> visibleDocIds = new HashSet<>();
        for (Document d : docMap.values()) {
            if (isAdmin) {
                visibleDocIds.add(d.getId());
            } else if (d.getOwnerId() == null) {
                visibleDocIds.add(d.getId()); // 公开文档
            } else if (userId != null && d.getOwnerId().equals(userId)) {
                visibleDocIds.add(d.getId()); // 自己的文档
            }
        }

        return candidates.stream()
                .filter(c -> visibleDocIds.contains(c.getDocumentId()))
                .collect(Collectors.toList());
    }

    private double scoreAt(List<RankedHit> ranked, int candidateIdx) {
        for (RankedHit r : ranked) {
            if (r.getIndex() == candidateIdx) return r.getScore();
        }
        return 0d;
    }

    private Map<Long, Document> lookupDocs(List<Integer> picked, List<DocumentChunk> candidates) {
        Map<Long, Document> map = new HashMap<>();
        if (picked.isEmpty()) return map;
        List<Long> ids = picked.stream().map(candidates::get).map(DocumentChunk::getDocumentId).distinct().collect(Collectors.toList());
        if (!ids.isEmpty()) {
            documentMapper.selectBatchIds(ids).forEach(d -> map.put(d.getId(), d));
        }
        return map;
    }

    /**
     * 构建多轮对话上下文：查询同一会话最近 3 条问答记录，拼接为对话历史文本。
     */
    private String buildConversationContext(String conversationId, Long userId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "";
        }
        try {
            LambdaQueryWrapper<QaHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(QaHistory::getConversationId, conversationId)
                   .orderByDesc(QaHistory::getCreatedAt)
                   .last("LIMIT 3");
            List<QaHistory> history = qaHistoryMapper.selectList(wrapper);
            if (history.isEmpty()) {
                return "";
            }
            // 倒序后反转，让最早的在前
            java.util.Collections.reverse(history);
            StringBuilder ctx = new StringBuilder();
            ctx.append("【对话历史】\n");
            for (QaHistory h : history) {
                ctx.append("问：").append(truncateText(h.getQuestion(), 500)).append("\n");
                ctx.append("答：").append(truncateText(h.getAnswer(), 500)).append("\n\n");
            }
            return ctx.toString().trim();
        } catch (Exception e) {
            log.warn("[QaService] buildConversationContext failed: {}", e.getMessage());
            return "";
        }
    }

    private String truncateText(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private QaAnswerVO saveAndReturn(String question, String answer, List<CitationVO> citations, Long ownerId, String conversationId) {
        QaHistory h = new QaHistory();
        h.setQuestion(question);
        h.setAnswer(answer == null ? "" : answer);
        h.setOwnerId(ownerId);
        h.setConversationId(conversationId);
        try {
            h.setCitations(objectMapper.writeValueAsString(citations == null ? Collections.emptyList() : citations));
        } catch (Exception e) {
            log.warn("[QaService] citations serialize failed: {}", e.getMessage());
            h.setCitations("[]");
        }
        qaHistoryMapper.insert(h);

        QaAnswerVO vo = new QaAnswerVO();
        vo.setId(h.getId());
        vo.setQuestion(h.getQuestion());
        vo.setAnswer(h.getAnswer());
        vo.setCitations(citations == null ? Collections.emptyList() : citations);
        vo.setCreatedAt(h.getCreatedAt());
        return vo;
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
            log.warn("[QaService] parse citations failed for id={}: {}", h.getId(), e.getMessage());
            vo.setCitations(Collections.emptyList());
        }
        vo.setConversationId(h.getConversationId());
        vo.setRating(h.getRating());
        vo.setUseful(h.getUseful());
        vo.setFeedback(h.getFeedback());
        vo.setCreatedAt(h.getCreatedAt());
        return vo;
    }

    // ==================== 评测 ====================

    @Override
    public void evaluate(Long historyId, EvaluateRequest req) {
        QaHistory h = qaHistoryMapper.selectById(historyId);
        if (h == null) {
            throw new BizException(ResultCode.NOT_FOUND, "问答记录不存在");
        }
        if (req.getRating() != null) {
            h.setRating(req.getRating());
        }
        if (req.getUseful() != null) {
            h.setUseful(req.getUseful());
        }
        if (req.getFeedback() != null) {
            h.setFeedback(req.getFeedback());
        }
        qaHistoryMapper.updateById(h);
        log.info("[QaService] evaluated history id={}, rating={}, useful={}", historyId, req.getRating(), req.getUseful());
    }

    @Override
    public Map<String, Object> evaluationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEvaluated", 0L);
        stats.put("avgRating", 0.0);
        stats.put("usefulCount", 0L);
        stats.put("usefulRate", 0.0);
        stats.put("distribution", new HashMap<>());
        try {
            List<QaHistory> all = qaHistoryMapper.selectList(
                    new LambdaQueryWrapper<QaHistory>().isNotNull(QaHistory::getRating));
            long total = all.size();
            stats.put("totalEvaluated", total);
            if (total > 0) {
                double avgRating = all.stream()
                        .filter(h -> h.getRating() != null)
                        .mapToInt(QaHistory::getRating)
                        .average()
                        .orElse(0);
                long usefulCount = all.stream().filter(h -> Boolean.TRUE.equals(h.getUseful())).count();
                stats.put("avgRating", Math.round(avgRating * 10) / 10.0);
                stats.put("usefulCount", usefulCount);
                stats.put("usefulRate", Math.round((double) usefulCount / total * 1000) / 10.0);
            }
            Map<Integer, Long> distribution = new HashMap<>();
            for (int i = 1; i <= 5; i++) {
                final int star = i;
                distribution.put(i, all.stream().filter(h -> Integer.valueOf(star).equals(h.getRating())).count());
            }
            stats.put("distribution", distribution);
        } catch (Exception e) {
            log.error("[QaService] evaluationStats failed", e);
            // 返回空统计，不阻断前端
        }
        return stats;
    }

    // ==================== AI 自动评测 ====================

    @Override
    public Map<String, Object> autoEvaluate(Long historyId, String model) {
        QaHistory h = qaHistoryMapper.selectById(historyId);
        if (h == null) {
            throw new BizException(ResultCode.NOT_FOUND, "问答记录不存在");
        }

        String evalJson = m3Service.autoEvaluate(h.getQuestion(), h.getAnswer(), model);
        log.info("[QaService] autoEvaluate result for id={}: {}", historyId, evalJson);

        // 解析评测结果
        Map<String, Object> evalResult = new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(evalJson, Map.class);
            evalResult.putAll(parsed);

            // 将 overall 评分写入 qa_history.rating（四舍五入取整）
            Object overall = parsed.get("overall");
            if (overall instanceof Number) {
                int rating = (int) Math.round(((Number) overall).doubleValue());
                rating = Math.max(1, Math.min(5, rating));
                h.setRating(rating);
            }
            // 将 comment 写入 feedback
            Object comment = parsed.get("comment");
            if (comment != null && !comment.toString().isBlank()) {
                h.setFeedback("[AI评测] " + comment.toString());
            }
            qaHistoryMapper.updateById(h);
            evalResult.put("saved", true);
        } catch (Exception e) {
            log.warn("[QaService] autoEvaluate parse failed: {}", e.getMessage());
            evalResult.put("error", "评测结果解析失败");
            evalResult.put("raw", evalJson);
        }

        return evalResult;
    }

    // ==================== 删除 ====================

    @Override
    public void deleteHistory(Long historyId, Long userId, boolean isAdmin) {
        QaHistory h = qaHistoryMapper.selectById(historyId);
        if (h == null) {
            throw new BizException(ResultCode.NOT_FOUND, "问答记录不存在");
        }
        // 权限检查：管理员可删所有，普通用户只能删自己的
        if (!isAdmin) {
            if (userId == null) {
                throw new BizException(ResultCode.FORBIDDEN, "请先登录");
            }
            if (h.getOwnerId() != null && !h.getOwnerId().equals(userId)) {
                throw new BizException(ResultCode.FORBIDDEN, "无权删除他人记录");
            }
        }
        qaHistoryMapper.deleteById(historyId);
        log.info("[QaService] deleted history id={}, by userId={}, isAdmin={}", historyId, userId, isAdmin);
    }
}
