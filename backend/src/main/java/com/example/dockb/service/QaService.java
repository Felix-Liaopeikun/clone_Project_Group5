package com.example.dockb.service;

import com.example.dockb.common.PageResult;
import com.example.dockb.dto.EvaluateRequest;
import com.example.dockb.vo.QaAnswerVO;
import com.example.dockb.vo.QaHistoryVO;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public interface QaService {

    /**
     * 问答（同步）。只检索当前用户有权限的文档。
     *
     * @param question       问题
     * @param topK           topK
     * @param model          指定模型，为空则用默认模型
     * @param conversationId 多轮对话会话 ID
     * @param userId         当前登录用户 ID（null=未登录，只能看到公开文档）
     * @param isAdmin        是否为管理员（管理员可看到所有文档）
     */
    QaAnswerVO ask(String question, Integer topK, String model, String conversationId, Long userId, boolean isAdmin);

    default QaAnswerVO ask(String question, Integer topK, String model, Long userId, boolean isAdmin) {
        return ask(question, topK, model, null, userId, isAdmin);
    }

    default QaAnswerVO ask(String question, Integer topK, Long userId, boolean isAdmin) {
        return ask(question, topK, null, null, userId, isAdmin);
    }

    default QaAnswerVO ask(String question, Integer topK) {
        return ask(question, topK, null, null, null, false);
    }

    /**
     * 分页历史（权限感知）。
     * @param userId  当前登录用户 ID（null=返回所有）
     * @param isAdmin 是否为管理员（管理员可看所有）
     */
    PageResult<QaHistoryVO> history(long page, long size, Long userId, boolean isAdmin);

    default PageResult<QaHistoryVO> history(long page, long size) {
        return history(page, size, null, false);
    }

    /**
     * 为流式问答构建上下文字符串列表（keyword 匹配，选取 topK，权限过滤）。
     */
    List<String> buildContext(String question, Integer topK, Long userId, boolean isAdmin);

    default List<String> buildContext(String question, Integer topK) {
        return buildContext(question, topK, null, false);
    }

    /**
     * 异步保存问答历史（不等待完成）。
     * @param ownerId        提问者 ID（可 null）
     * @param conversationId 多轮对话会话 ID（可 null）
     */
    void saveHistoryAsync(String question, String answer, Long ownerId, String conversationId);

    // ========== 评测 ==========

    /**
     * 评测问答记录。
     * @param historyId 问答历史 ID
     * @param req 评分/是否有用/反馈
     */
    void evaluate(Long historyId, EvaluateRequest req);

    /** 获取评测统计数据。 */
    Map<String, Object> evaluationStats();

    // ========== AI 自动评测 ==========

    /**
     * AI 自动评测问答回答，并更新评分到数据库。
     * @return 评测结果（含各维度分数和总评）
     */
    Map<String, Object> autoEvaluate(Long historyId, String model);

    // ========== 删除 ==========

    /**
     * 删除问答历史记录（权限感知）。
     * @param historyId 记录 ID
     * @param userId    当前用户 ID
     * @param isAdmin   是否管理员
     */
    void deleteHistory(Long historyId, Long userId, boolean isAdmin);

    default void saveHistoryAsync(String question, String answer) {
        saveHistoryAsync(question, answer, null, null);
    }

    /**
     * 流式问答：返回逐 token 的 Flux。
     * @param conversationId 多轮对话会话 ID
     * @param userId         当前登录用户 ID（null=未登录，只能看到公开文档）
     * @param isAdmin        是否为管理员
     */
    Flux<String> askStream(String question, Integer topK, String model, String conversationId, Long userId, boolean isAdmin);

    default Flux<String> askStream(String question, Integer topK, String model, Long userId, boolean isAdmin) {
        return askStream(question, topK, model, null, userId, isAdmin);
    }

    default Flux<String> askStream(String question, Integer topK, String model) {
        return askStream(question, topK, model, null, null, false);
    }
}
