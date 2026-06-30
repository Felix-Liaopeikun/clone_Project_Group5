package com.example.dockb.controller;

import com.example.dockb.common.Result;
import com.example.dockb.dto.AgentRequest;
import com.example.dockb.annotation.RequireRole;
import com.example.dockb.service.AgentService;
import com.example.dockb.util.AuthContext;
import com.example.dockb.vo.AgentResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能体问答 API。
 */
@Slf4j
@RestController
@RequestMapping("/api/qa/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /** 执行智能体推理 */
    @PostMapping
    @RequireRole(RequireRole.Role.USER)
    public Result<AgentResponseVO> execute(@RequestBody @Valid AgentRequest req,
                                            HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        boolean isAdmin = AuthContext.isAdmin(request);
        log.info("[Agent] question={}, maxSteps={}", req.getQuestion(), req.getMaxSteps());
        return Result.success(agentService.execute(req, userId, isAdmin));
    }

    /** 获取可用工具列表 */
    @GetMapping("/tools")
    public Result<List<Map<String, Object>>> listTools() {
        return Result.success(agentService.listTools());
    }
}
