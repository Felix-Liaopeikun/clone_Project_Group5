package com.example.dockb.controller;

import com.example.dockb.annotation.RequireRole;
import com.example.dockb.common.Result;
import com.example.dockb.config.CurrentUser;
import com.example.dockb.dto.EvaluateRequest;
import com.example.dockb.dto.QaAskRequest;
import com.example.dockb.service.QaService;
import com.example.dockb.util.AuthContext;
import com.example.dockb.vo.QaAnswerVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 问答接口（契约 §5.4）。
 *
 * <p>权限说明：
 * <ul>
 *   <li>问答题源：只检索当前用户有权限的文档（与 search 一致）</li>
 *   <li>历史记录：ADMIN 可见全部；USER 可见自己和匿名记录</li>
 *   <li>history 接口：需要登录（未登录返回 401）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    /**
     * 同步问答（可匿名调用，但只能检索公开文档）。
     */
    @PostMapping("/ask")
    public Result<QaAnswerVO> ask(@Valid @RequestBody QaAskRequest req, HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        boolean isAdmin = AuthContext.isAdmin(request);
        log.info("[QA] ask: question={}, topK={}, model={}, convId={}, userId={}, isAdmin={}",
                req.getQuestion(), req.getTopK(), req.getModel(), req.getConversationId(), userId, isAdmin);
        return Result.success(qaService.ask(req.getQuestion(), req.getTopK(), req.getModel(), req.getConversationId(), userId, isAdmin));
    }

    /**
     * 流式问答 SSE 端点（可匿名调用，但只能检索公开文档）。
     * 逐 token 返回 M3 生成内容，前端可逐字显示。
     * 最终发送 [DONE] 后，异步将完整答案存入历史。
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(@Valid @RequestBody QaAskRequest req, HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        boolean isAdmin = AuthContext.isAdmin(request);
        log.info("[QA] stream ask: question={}, topK={}, model={}, convId={}, userId={}, isAdmin={}",
                req.getQuestion(), req.getTopK(), req.getModel(), req.getConversationId(), userId, isAdmin);
        Flux<String> stream = qaService.askStream(req.getQuestion(), req.getTopK(), req.getModel(), req.getConversationId(), userId, isAdmin);

        // 收集完整答案，结束后异步存库
        StringBuilder fullAnswer = new StringBuilder();
        return stream
                .doOnNext(token -> {
                    if (!"[DONE]".equals(token)) {
                        fullAnswer.append(token);
                    }
                })
                .doOnComplete(() -> {
                    String answer = fullAnswer.toString();
                    if (!answer.isEmpty()) {
                        log.info("[QA] stream done, answer length={}", answer.length());
                        qaService.saveHistoryAsync(req.getQuestion(), answer, userId, req.getConversationId());
                    }
                })
                .onErrorResume(e -> {
                    log.error("[QA] stream error: {}", e.getMessage());
                    String errMsg = e instanceof com.example.dockb.client.M3Exception ? e.getMessage() : "AI 服务暂时不可用，请稍后重试";
                    return Flux.just("[AI错误] " + errMsg, "[DONE]");
                });
    }

    /**
     * 问答历史（需要登录）。
     * ADMIN 可见全部；USER 可见自己和匿名记录。
     */
    @RequireRole(RequireRole.Role.USER)
    @GetMapping("/history")
    public Result<?> history(@RequestParam(defaultValue = "1") long page,
                             @RequestParam(defaultValue = "10") long size,
                             HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        if (userId == null) {
            return Result.fail(401, "请先登录后查看历史记录");
        }
        boolean isAdmin = AuthContext.isAdmin(request);
        return Result.success(qaService.history(page, size, userId, isAdmin));
    }

    /**
     * 评测问答回答。
     */
    @RequireRole(RequireRole.Role.USER)
    @PostMapping("/evaluate/{id}")
    public Result<?> evaluate(@PathVariable Long id,
                              @Valid @RequestBody EvaluateRequest req) {
        qaService.evaluate(id, req);
        return Result.success();
    }

    /**
     * 删除问答历史记录（权限感知）。
     */
    @RequireRole(RequireRole.Role.USER)
    @DeleteMapping("/history/{id}")
    public Result<?> deleteHistory(@PathVariable Long id, HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        boolean isAdmin = AuthContext.isAdmin(request);
        qaService.deleteHistory(id, userId, isAdmin);
        return Result.success();
    }

    /**
     * AI 自动评测问答回答质量。
     */
    @RequireRole(RequireRole.Role.USER)
    @PostMapping("/evaluate/auto/{id}")
    public Result<Map<String, Object>> autoEvaluate(@PathVariable Long id,
                                                     @RequestParam(required = false) String model) {
        return Result.success(qaService.autoEvaluate(id, model));
    }

    /**
     * 评测统计。
     */
    @GetMapping("/evaluation-stats")
    public Result<Map<String, Object>> evaluationStats() {
        return Result.success(qaService.evaluationStats());
    }
}
