package com.example.dockb.client;

import com.example.dockb.client.dto.ChatRequest;
import com.example.dockb.client.dto.ChatResponse;
import com.example.dockb.client.dto.QaResult;
import com.example.dockb.client.dto.RankedHit;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 模型客户端接口。
 *
 * <p>支持多模型：每个方法均有 model 参数版本，若 model 为空则使用默认模型。
 * 所有方法在模型不可达或解析失败时抛 {@link M3Exception}。
 */
public interface M3Client {

    /** 简单可达性探测。 */
    boolean ping();

    // ==================== 分类 ====================

    String classify(String text, List<String> candidates);

    /**
     * 指定模型分类。
     * @param model 若为空则使用配置的默认模型
     */
    String classify(String text, List<String> candidates, String model);

    // ==================== 摘要 ====================

    String summarize(String text);

    String summarize(String text, String model);

    // ==================== 标签抽取 ====================

    List<String> extractTags(String text);

    List<String> extractTags(String text, String model);

    // ==================== 重排 ====================

    List<RankedHit> rerank(String query, List<String> candidates);

    List<RankedHit> rerank(String query, List<String> candidates, String model);

    // ==================== 问答 ====================

    QaResult answer(String question, List<String> context);

    QaResult answer(String question, List<String> context, String model);

    // ==================== 流式问答 ====================

    Flux<String> answerStream(String question, List<String> context);

    Flux<String> answerStream(String question, List<String> context, String model);

    // ==================== AI 自动评测 ====================

    /**
     * AI 自动评测问答回答质量。
     * @return JSON 格式评测结果字符串
     */
    String evaluateAnswer(String question, String answer);

    String evaluateAnswer(String question, String answer, String model);

    // ==================== 通用 ====================

    /** 通用调用入口（用于自定义 prompt）。 */
    ChatResponse chat(ChatRequest request);
}