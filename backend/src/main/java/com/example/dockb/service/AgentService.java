package com.example.dockb.service;

import com.example.dockb.dto.AgentRequest;
import com.example.dockb.vo.AgentResponseVO;

import java.util.List;
import java.util.Map;

/**
 * 智能体服务 — ReAct 风格多步推理。
 */
public interface AgentService {

    /** 执行智能体推理 */
    AgentResponseVO execute(AgentRequest req, Long userId, boolean isAdmin);

    /** 获取可用工具列表（供前端展示） */
    List<Map<String, Object>> listTools();
}
