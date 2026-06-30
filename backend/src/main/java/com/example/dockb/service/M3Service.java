package com.example.dockb.service;

import com.example.dockb.client.dto.QaResult;
import com.example.dockb.client.dto.RankedHit;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 模型编排服务：调用模型客户端，失败时降级到本地启发式。
 *
 * <p>支持按请求指定模型（model 参数），或使用当前激活的默认模型。
 */
public interface M3Service {

    /** 模型可达性探测。 */
    boolean isReachable(String model);

    // ==================== 分类 ====================

    String classifyWithFallback(String text, List<String> candidates, String defaultCategory);

    String classifyWithFallback(String text, List<String> candidates, String defaultCategory, String model);

    // ==================== 摘要 ====================

    String summarizeWithFallback(String text, String defaultSummary);

    String summarizeWithFallback(String text, String defaultSummary, String model);

    // ==================== 标签抽取 ====================

    List<String> extractTagsWithFallback(String text, List<String> defaults);

    List<String> extractTagsWithFallback(String text, List<String> defaults, String model);

    // ==================== 重排 ====================

    /**
     * 重排：模型失败时降级为 TF 词频打分。
     */
    List<RankedHit> rerankWithFallback(String query, List<String> candidates);

    List<RankedHit> rerankWithFallback(String query, List<String> candidates, String model);

    // ==================== 问答 ====================

    /**
     * 问答：返回 {@link QaResult}。模型失败时构造一个简单回退答案。
     */
    QaResult answerWithFallback(String question, List<String> context);

    QaResult answerWithFallback(String question, List<String> context, String model);

    /**
     * 流式问答：返回逐 token 的 Flux。
     * @param question 问题
     * @param context 上下文片段
     * @param model 模型名，若为空则用默认模型
     */
    Flux<String> answerStream(String question, List<String> context, String model);

    // ==================== AI 自动评测 ====================

    /**
     * AI 自动评测问答质量，返回 JSON 评测结果。
     */
    String autoEvaluate(String question, String answer, String model);
}