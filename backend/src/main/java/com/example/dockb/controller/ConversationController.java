package com.example.dockb.controller;

import com.example.dockb.common.Result;
import com.example.dockb.annotation.RequireRole;
import com.example.dockb.service.ConversationService;
import com.example.dockb.util.AuthContext;
import com.example.dockb.vo.ConversationVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 对话管理 API */
@Slf4j
@RestController
@RequestMapping("/api/qa/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    @RequireRole(RequireRole.Role.USER)
    public Result<List<ConversationVO>> list(HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        boolean isAdmin = AuthContext.isAdmin(request);
        return Result.success(conversationService.listConversations(userId, isAdmin));
    }

    @GetMapping("/{id}")
    @RequireRole(RequireRole.Role.USER)
    public Result<ConversationVO> get(@PathVariable String id, HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        boolean isAdmin = AuthContext.isAdmin(request);
        return Result.success(conversationService.getConversation(id, userId, isAdmin));
    }

    @DeleteMapping("/{id}")
    @RequireRole(RequireRole.Role.USER)
    public Result<?> delete(@PathVariable String id, HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        boolean isAdmin = AuthContext.isAdmin(request);
        conversationService.deleteConversation(id, userId, isAdmin);
        return Result.success(java.util.Map.of("deleted", true));
    }

    @GetMapping("/{id}/export")
    @RequireRole(RequireRole.Role.USER)
    public ResponseEntity<byte[]> export(@PathVariable String id,
                                          @RequestParam(defaultValue = "md") String format,
                                          HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        boolean isAdmin = AuthContext.isAdmin(request);
        String content = conversationService.exportConversation(id, format, userId, isAdmin);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=conversation-" + id + ".md")
                .contentType(MediaType.TEXT_MARKDOWN)
                .body(bytes);
    }
}
